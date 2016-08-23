/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class TrailingSpacesStripper extends FileDocumentManagerAdapter {
  public static final Key<String> OVERRIDE_STRIP_TRAILING_SPACES_KEY = Key.create("OVERRIDE_TRIM_TRAILING_SPACES_KEY");
  public static final Key<Boolean> OVERRIDE_ENSURE_NEWLINE_KEY = Key.create("OVERRIDE_ENSURE_NEWLINE_KEY");

  private static final Key<Boolean> DISABLE_FOR_FILE_KEY = Key.create("DISABLE_TRAILING_SPACE_STRIPPER_FOR_FILE_KEY");

  private final Set<Document> myDocumentsToStripLater = new THashSet<>();

  @Override
  public void beforeAllDocumentsSaving() {
    Set<Document> documentsToStrip = new THashSet<>(myDocumentsToStripLater);
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
    if (!document.isWritable()) return;
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile file = fileDocumentManager.getFile(document);
    if (file == null || !file.isValid() || Boolean.TRUE.equals(DISABLE_FOR_FILE_KEY.get(file))) return;

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null) return;

    final String overrideStripTrailingSpacesData = file.getUserData(OVERRIDE_STRIP_TRAILING_SPACES_KEY);
    final Boolean overrideEnsureNewlineData = file.getUserData(OVERRIDE_ENSURE_NEWLINE_KEY);
    @EditorSettingsExternalizable.StripTrailingSpaces
    String stripTrailingSpaces = overrideStripTrailingSpacesData != null ? overrideStripTrailingSpacesData : settings.getStripTrailingSpaces();
    final boolean doStrip = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    final boolean ensureEOL = overrideEnsureNewlineData != null ? overrideEnsureNewlineData.booleanValue() : settings.isEnsureNewLineAtEOF();

    if (doStrip) {
      final boolean inChangedLinesOnly = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
      boolean success = stripIfNotCurrentLine(document, inChangedLinesOnly);
      if (!success) {
        myDocumentsToStripLater.add(document);
      }
    }

    final int lines = document.getLineCount();
    if (ensureEOL && lines > 0) {
      final int start = document.getLineStartOffset(lines - 1);
      final int end = document.getLineEndOffset(lines - 1);
      if (start != end) {
        final CharSequence content = document.getCharsSequence();
        ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(document, null) {
          @Override
          public void run() {
            CommandProcessor.getInstance().runUndoTransparentAction(() -> {
              if (CharArrayUtil.containsOnlyWhiteSpaces(content.subSequence(start, end)) && doStrip) {
                document.deleteString(start, end);
              }
              else {
                document.insertString(end, "\n");
              }
            });
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

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null) return;

    boolean enabled = !Boolean.TRUE.equals(DISABLE_FOR_FILE_KEY.get(FileDocumentManager.getInstance().getFile(document)));
    if (!enabled) return;
    String stripTrailingSpaces = settings.getStripTrailingSpaces();
    final boolean doStrip = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    final boolean inChangedLinesOnly = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);

    int[] caretLines;
    if (activeEditor != null && inChangedLinesOnly && doStrip && !isVirtualSpaceEnabled) {
      List<Caret> carets = activeEditor.getCaretModel().getAllCarets();
      caretLines = new int[carets.size()];
      for (int i = 0; i < carets.size(); i++) {
        Caret caret = carets.get(i);
        caretLines[i] = caret.getLogicalPosition().line;
      }
    }
    else {
      caretLines = ArrayUtil.EMPTY_INT_ARRAY;
    }
    ((DocumentImpl)document).clearLineModificationFlagsExcept(caretLines);
  }

  private static Editor getActiveEditor(@NotNull Document document) {
    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    boolean isDisposeInProgress = ApplicationManager.getApplication().isDisposeInProgress(); // ignore caret placing when exiting
    Editor activeEditor = isDisposeInProgress ? null : CommonDataKeys.EDITOR.getData(dataContext);
    if (activeEditor != null && activeEditor.getDocument() != document) {
      activeEditor = null;
    }
    return activeEditor;
  }

  public static boolean stripIfNotCurrentLine(@NotNull Document document, boolean inChangedLinesOnly) {
    if (document instanceof DocumentWindow) {
      document = ((DocumentWindow)document).getDelegate();
    }
    if (!(document instanceof DocumentImpl)) {
      return true;
    }
    Editor activeEditor = getActiveEditor(document);

    // when virtual space enabled, we can strip whitespace anywhere
    boolean isVirtualSpaceEnabled = activeEditor == null || activeEditor.getSettings().isVirtualSpace();

    final List<Caret> carets = activeEditor == null ? Collections.<Caret>emptyList() : activeEditor.getCaretModel().getAllCarets();
    final List<VisualPosition> visualCarets = new ArrayList<>(carets.size());
    int[] caretOffsets = new int[carets.size()];
    for (int i = 0; i < carets.size(); i++) {
      Caret caret = carets.get(i);
      visualCarets.add(caret.getVisualPosition());
      caretOffsets[i] = caret.getOffset();
    }

    boolean markAsNeedsStrippingLater =
      ((DocumentImpl)document).stripTrailingSpaces(getProject(document, activeEditor),
                                                   inChangedLinesOnly, isVirtualSpaceEnabled, caretOffsets);

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
}
