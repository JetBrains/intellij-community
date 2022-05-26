// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
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

    if (options.isRemoveTrailingBlankLines()) {
      removeTrailngBlankLines(document, options.isEnsureNewLineAtEOF());
    }

    final int lines = document.getLineCount();
    if (options.isEnsureNewLineAtEOF() && lines > 0) {
      final int start = document.getLineStartOffset(lines - 1);
      final int end = document.getLineEndOffset(lines - 1);
      if (start != end) {
        final CharSequence content = document.getCharsSequence();
        performUndoableWrite(new DocumentRunnable(document, null) {
          @Override
          public void run() {
            if (CharArrayUtil.containsOnlyWhiteSpaces(content.subSequence(start, end)) && options.isStripTrailingSpaces() &&
                !(options.isKeepTrailingSpacesOnCaretLine() && hasCaretIn(start, end))) {
              document.deleteString(start, end);
            }
            else {
              document.insertString(end, "\n");
            }
          }

          private boolean hasCaretIn(int start, int end) {
            for (Editor activeEditor : getActiveEditors(document)) {
              for (Caret caret : activeEditor.getCaretModel().getAllCarets()) {
                int offset = caret.getOffset();
                if (offset >= start && offset <= end) return true;
              }
            }
            return false;
          }
        });
      }
    }
  }

  private static void removeTrailngBlankLines(@NotNull Document document, boolean keepLast) {
    if (document.getLineCount() > 0) {
      int endOffset = document.getTextLength() - 1;
      Ref<Integer> deleteToExclusive = Ref.create(endOffset + 1);
      CharSequence content = document.getCharsSequence();
      int blankAreaOffset = CharArrayUtil.shiftBackward(content, endOffset, " \t\r\n" );
      if (blankAreaOffset < endOffset) {
        final int firstNewLineOffset = CharArrayUtil.indexOf(content, "\n", blankAreaOffset);
        if (firstNewLineOffset > 0) {
          if (keepLast) {
            int lastLNewLineOffset = CharArrayUtil.lastIndexOf(content, "\n", endOffset);
            if (lastLNewLineOffset >= firstNewLineOffset) {
              deleteToExclusive.set(lastLNewLineOffset);
            }
          }
          performUndoableWrite(new DocumentRunnable(document, null) {
            @Override
            public void run() {
              document.deleteString(firstNewLineOffset, deleteToExclusive.get());
            }
          });
        }
      }
    }
  }

  private static void performUndoableWrite(@NotNull DocumentRunnable documentRunnable) {
    ApplicationManager.getApplication().runWriteAction(
      () -> CommandProcessor.getInstance().runUndoTransparentAction(documentRunnable)
    );
  }

  // clears line modification flags except lines which was not stripped because the caret was in the way
  public void clearLineModificationFlags(@NotNull Document document) {
    if (document instanceof DocumentWindow) {
      document = ((DocumentWindow)document).getDelegate();
    }
    if (!(document instanceof DocumentImpl)) {
      return;
    }

    TrailingSpacesOptions options = getOptions(document);
    if (options == null) return;

    List<Editor> activeEditors = getActiveEditors(document);
    List<Caret> carets = new ArrayList<>();
    if (options.isChangedLinesOnly() && options.isStripTrailingSpaces()) {
      for (Editor activeEditor : activeEditors) {
        // when virtual space enabled, we can strip whitespace anywhere
        if (!activeEditor.getSettings().isVirtualSpace()) {
          carets.addAll(activeEditor.getCaretModel().getAllCarets());
        }
      }
    }

    int[] caretLines;
    if (!carets.isEmpty()) {
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

  private static List<Editor> getActiveEditors(@NotNull Document document) {
    Application application = ApplicationManager.getApplication();
    // ignore caret placing when exiting
    if (application.isDisposed()) {
      return Collections.emptyList();
    }
    List<Editor> activeEditors = new ArrayList<>();
    Editor localEditor = getActiveLocalEditor(document);
    if (localEditor != null) {
      activeEditors.add(localEditor);
    }
    for (ClientEditorManager manager : application.getServices(ClientEditorManager.class, false)) {
      manager.editors().filter(e -> UIUtil.hasFocus(e.getComponent()) && e.getDocument() == document).forEach(activeEditors::add);
    }
    return activeEditors;
  }

  private static Editor getActiveLocalEditor(@NotNull Document document) {
    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    Editor activeEditor = CommonDataKeys.EDITOR.getData(dataContext);
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
    List<Editor> activeEditors = getActiveEditors(document);

    final List<Caret> carets = new ArrayList<>();
    for (Editor activeEditor : activeEditors) {
      carets.addAll(activeEditor.getCaretModel().getAllCarets());
    }
    final List<VisualPosition> visualCarets = new ArrayList<>(carets.size());
    int[] caretOffsets = new int[carets.size()];
    for (int i = 0; i < carets.size(); i++) {
      Caret caret = carets.get(i);
      visualCarets.add(caret.getVisualPosition());
      caretOffsets[i] = caret.getOffset();
    }

    boolean markAsNeedsStrippingLater = ((DocumentImpl)document)
      .stripTrailingSpaces(getProject(document, activeEditors), inChangedLinesOnly, skipCaretLines ? caretOffsets : null);

    if (!activeEditors.isEmpty() && !ShutDownTracker.isShutdownHookRunning()) {
      runBatchCaretOperation(activeEditors, () -> {
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

  private static void runBatchCaretOperation(@NotNull List<Editor> editors, @NotNull Runnable runnable) {
    runBatchCaretOperation(editors, 0, runnable);
  }

  private static void runBatchCaretOperation(@NotNull List<Editor> editors, int startIndex, @NotNull Runnable runnable) {
    if (startIndex >= editors.size()) {
      runnable.run();
      return;
    }
    editors.get(startIndex).getCaretModel().runBatchCaretOperation(() -> {
      runBatchCaretOperation(editors, startIndex + 1, runnable);
    });
  }

  @Nullable
  private static Project getProject(@NotNull Document document, @NotNull List<Editor> editors) {
    for (Editor editor : editors) {
      Project project = editor.getProject();
      if (project != null) {
        return project;
      }
    }
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
          final List<Editor> activeEditors = getActiveEditors(document);
          final Project project = getProject(document, activeEditors);
          MyTrailingSpacesOptions currOptions = new MyTrailingSpacesOptions();
          if (project != null) {
            for (TrailingSpacesOptionsProvider provider : TrailingSpacesOptionsProvider.EP_NAME.getExtensionList()) {
              TrailingSpacesOptionsProvider.Options providerOptions = provider.getOptions(project, file);
              if (providerOptions != null) {
                currOptions.setStripTrailingSpaces(providerOptions.getStripTrailingSpaces());
                currOptions.setEnsureNewLineAtEOF(providerOptions.getEnsureNewLineAtEOF());
                currOptions.setRemoveTrailingBlankLines(providerOptions.getRemoveTrailingBlankLines());
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
    private @Nullable Boolean myRemoveTrailingBlankLines;
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

    private void setRemoveTrailingBlankLines(@Nullable Boolean removeTrailingBlankLines) {
      if (removeTrailingBlankLines != null && myRemoveTrailingBlankLines == null) {
        myRemoveTrailingBlankLines = removeTrailingBlankLines;
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
    public boolean isRemoveTrailingBlankLines() {
      return myRemoveTrailingBlankLines != null ? myRemoveTrailingBlankLines.booleanValue() : myEditorSettings.isRemoveTrailingBlankLines();
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
