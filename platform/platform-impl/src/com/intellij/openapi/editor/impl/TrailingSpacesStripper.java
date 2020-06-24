// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.TrailingSpacesOptions;
import com.intellij.openapi.fileEditor.TrailingSpacesOptionsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

public final class TrailingSpacesStripper implements FileDocumentManagerListener {
  private static final Key<Boolean> DISABLE_FOR_FILE_KEY = Key.create("DISABLE_TRAILING_SPACE_STRIPPER_FOR_FILE_KEY");

  private final Set<Document> myDocumentsToStripLater = new HashSet<>();

  @Override
  public void beforeAllDocumentsSaving() {
    Set<Document> documentsToStrip = new HashSet<>(myDocumentsToStripLater);
    myDocumentsToStripLater.clear();
    for (Document document : documentsToStrip) {
      strip(document);
    }
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    strip(document);
  }

  private void strip(@NotNull final Document document) {
    TrailingSpacesOptions options = getOptions(document);
    if (options == null) return;

    if (options.isStripTrailingSpaces()) {
      boolean success = strip(document, options.isChangedLinesOnly(), options.isKeepTrailingSpacesOnCaretLine());
      if (!success) {
        myDocumentsToStripLater.add(document);
      }
    }

    final int lines = document.getLineCount();
    if (options.isEnsureNewLineAtEOF() && lines > 0) {
      final int start = document.getLineStartOffset(lines - 1);
      final int end = document.getLineEndOffset(lines - 1);
      if (start != end) {
        final CharSequence content = document.getCharsSequence();
        ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(document, null) {
          @Override
          public void run() {
            CommandProcessor.getInstance().runUndoTransparentAction(() -> {
              if (CharArrayUtil.containsOnlyWhiteSpaces(content.subSequence(start, end)) && options.isStripTrailingSpaces() &&
                  !(options.isKeepTrailingSpacesOnCaretLine() && hasCaretIn(start, end))) {
                document.deleteString(start, end);
              }
              else {
                document.insertString(end, "\n");
              }
            });
          }

          private boolean hasCaretIn(int start, int end) {
            Editor activeEditor = getActiveEditor(document);
            final List<Caret> carets = activeEditor == null ? Collections.emptyList() : activeEditor.getCaretModel().getAllCarets();
            for (Caret caret : carets) {
              int offset = caret.getOffset();
              if (offset >= start && offset <= end) return true;
            }
            return false;
          }
        });
      }
    }
  }

  // clears line modification flags except lines which was not stripped because the caret was in the way
  public void clearLineModificationFlags(@NotNull Document document) {
    if (document instanceof DocumentWindow) {
      document = ((DocumentWindow)document).getDelegate();
    }
    if (!(document instanceof DocumentImpl)) {
      return;
    }

    Editor activeEditor = getActiveEditor(document);

    // when virtual space enabled, we can strip whitespace anywhere
    boolean isVirtualSpaceEnabled = activeEditor == null || activeEditor.getSettings().isVirtualSpace();

    TrailingSpacesOptions options = getOptions(document);
    if (options == null) return;

    int[] caretLines;
    if (activeEditor != null && options.isChangedLinesOnly() && options.isStripTrailingSpaces() && !isVirtualSpaceEnabled) {
      List<Caret> carets = activeEditor.getCaretModel().getAllCarets();
      caretLines = new int[carets.size()];
      for (int i = 0; i < carets.size(); i++) {
        Caret caret = carets.get(i);
        caretLines[i] = caret.getLogicalPosition().line;
      }
    }
    else {
      caretLines = ArrayUtilRt.EMPTY_INT_ARRAY;
    }
    ((DocumentImpl)document).clearLineModificationFlagsExcept(caretLines);
  }

  private static Editor getActiveEditor(@NotNull Document document) {
    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    // ignore caret placing when exiting
    Editor activeEditor = ApplicationManager.getApplication().isDisposed() ? null : CommonDataKeys.EDITOR.getData(dataContext);
    if (activeEditor != null && activeEditor.getDocument() != document) {
      activeEditor = null;
    }
    return activeEditor;
  }

  public static boolean strip(@NotNull Document document, boolean inChangedLinesOnly, boolean skipCaretLines) {
    if (document instanceof DocumentWindow) {
      document = ((DocumentWindow)document).getDelegate();
    }
    if (!(document instanceof DocumentImpl)) {
      return true;
    }
    Editor activeEditor = getActiveEditor(document);

    final List<Caret> carets = activeEditor == null ? Collections.emptyList() : activeEditor.getCaretModel().getAllCarets();
    final List<VisualPosition> visualCarets = new ArrayList<>(carets.size());
    int[] caretOffsets = new int[carets.size()];
    for (int i = 0; i < carets.size(); i++) {
      Caret caret = carets.get(i);
      visualCarets.add(caret.getVisualPosition());
      caretOffsets[i] = caret.getOffset();
    }

    boolean markAsNeedsStrippingLater = ((DocumentImpl)document)
      .stripTrailingSpaces(getProject(document, activeEditor), inChangedLinesOnly, skipCaretLines ? caretOffsets : null);

    if (activeEditor != null && !ShutDownTracker.isShutdownHookRunning()) {
      activeEditor.getCaretModel().runBatchCaretOperation(() -> {
        for (int i = 0; i < carets.size(); i++) {
          Caret caret = carets.get(i);
          if (caret.isValid()) {
            caret.moveToVisualPosition(visualCarets.get(i));
          }
        }
      });
    }

    return !markAsNeedsStrippingLater;
  }

  @Nullable
  private static Project getProject(@NotNull Document document, @Nullable Editor editor) {
    if (editor != null) return editor.getProject();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      return ProjectUtil.guessProjectForFile(file);
    }
    return null;
  }

  public void documentDeleted(@NotNull Document doc) {
    myDocumentsToStripLater.remove(doc);
  }

  @Override
  public void unsavedDocumentsDropped() {
    myDocumentsToStripLater.clear();
  }

  public static void setEnabled(@NotNull VirtualFile file, boolean enabled) {
    DISABLE_FOR_FILE_KEY.set(file, enabled ? null : Boolean.TRUE);
  }

  public static boolean isEnabled(@NotNull VirtualFile file) {
    return !Boolean.TRUE.equals(DISABLE_FOR_FILE_KEY.get(file));
  }

  @Nullable
  public static TrailingSpacesOptions getOptions(@NotNull Document document) {
    if (document.isWritable()) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile file = fileDocumentManager.getFile(document);
      if (file != null && file.isValid() && !Boolean.TRUE.equals(DISABLE_FOR_FILE_KEY.get(file))) {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        if (editorSettings != null) {
          final Editor activeEditor = getActiveEditor(document);
          final Project project = getProject(document, activeEditor);
          MyTrailingSpacesOptions currOptions = new MyTrailingSpacesOptions();
          if (project != null) {
            for (TrailingSpacesOptionsProvider provider : TrailingSpacesOptionsProvider.EP_NAME.getExtensionList()) {
              TrailingSpacesOptionsProvider.Options providerOptions = provider.getOptions(project, file);
              if (providerOptions != null) {
                currOptions.setStripTrailingSpaces(providerOptions.getStripTrailingSpaces());
                currOptions.setEnsureNewLineAtEOF(providerOptions.getEnsureNewLineAtEOF());
                currOptions.setChangedLinesOnly(providerOptions.getChangedLinesOnly());
                currOptions.setKeepTrailingSpacesOnCaretLine(providerOptions.getKeepTrailingSpacesOnCaretLine());
              }
            }
          }
          return currOptions;
        }
      }
    }
    return null;
  }

  private static final class MyTrailingSpacesOptions implements TrailingSpacesOptions {
    private @Nullable Boolean myStripTrailingSpaces;
    private @Nullable Boolean myEnsureNewLineAtEOF;
    private @Nullable Boolean myChangedLinesOnly;
    private @Nullable Boolean myKeepTrailingSpacesOnCaretLine;

    private final EditorSettingsExternalizable myEditorSettings;

    private MyTrailingSpacesOptions() {
      myEditorSettings = EditorSettingsExternalizable.getInstance();
    }

    private void setStripTrailingSpaces(@Nullable Boolean stripTrailingSpaces) {
      if (stripTrailingSpaces != null && myStripTrailingSpaces == null) {
        myStripTrailingSpaces = stripTrailingSpaces;
      }
    }

    private void setEnsureNewLineAtEOF(@Nullable Boolean ensureNewLineAtEOF) {
      if (ensureNewLineAtEOF != null && myEnsureNewLineAtEOF == null) {
        myEnsureNewLineAtEOF = ensureNewLineAtEOF;
      }
    }

    private void setChangedLinesOnly(@Nullable Boolean changedLinesOnly) {
      if (changedLinesOnly != null && myChangedLinesOnly == null) {
        myChangedLinesOnly = changedLinesOnly;
      }
    }

    private void setKeepTrailingSpacesOnCaretLine(@Nullable Boolean keepTrailingSpacesOnCaretLine) {
      if (keepTrailingSpacesOnCaretLine != null && myKeepTrailingSpacesOnCaretLine == null) {
        myKeepTrailingSpacesOnCaretLine = keepTrailingSpacesOnCaretLine;
      }
    }

    @Override
    public boolean isStripTrailingSpaces() {
      return myStripTrailingSpaces != null
             ? myStripTrailingSpaces.booleanValue()
             : !EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE.equals(myEditorSettings.getStripTrailingSpaces());
    }

    @Override
    public boolean isEnsureNewLineAtEOF() {
      return myEnsureNewLineAtEOF != null
             ? myEnsureNewLineAtEOF.booleanValue()
             : myEditorSettings.isEnsureNewLineAtEOF();
    }

    @Override
    public boolean isChangedLinesOnly() {
      return myChangedLinesOnly != null
             ? myChangedLinesOnly.booleanValue()
             : !EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE.equals(myEditorSettings.getStripTrailingSpaces());
    }

    @Override
    public boolean isKeepTrailingSpacesOnCaretLine() {
      return myKeepTrailingSpacesOnCaretLine != null
             ? myKeepTrailingSpacesOnCaretLine.booleanValue()
             : myEditorSettings.isKeepTrailingSpacesOnCaretLine();
    }
  }
}
