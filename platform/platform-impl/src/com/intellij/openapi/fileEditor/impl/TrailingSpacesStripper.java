/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Set;

public final class TrailingSpacesStripper extends FileDocumentManagerAdapter {
  private final Set<Document> myDocumentsToStripLater = new THashSet<Document>();

  @Override
  public void beforeAllDocumentsSaving() {
    Set<Document> documentsToStrip = new THashSet<Document>(myDocumentsToStripLater);
    myDocumentsToStripLater.clear();
    for (Document document : documentsToStrip) {
      strip(document);
    }
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    strip(document);
  }

  private void strip(final Document document) {
    if (!document.isWritable()) return;
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile file = fileDocumentManager.getFile(document);
    if (file == null || !file.isValid()) return;

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null) return;

    String stripTrailingSpaces = settings.getStripTrailingSpaces();
    final boolean doStrip = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    final boolean ensureEOL = settings.isEnsureNewLineAtEOF();

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
            CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
              @Override
              public void run() {
                if (CharArrayUtil.containsOnlyWhiteSpaces(content.subSequence(start, end)) && doStrip) {
                  document.deleteString(start, end);
                }
                else {
                  document.insertString(end, "\n");
                }
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

    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    boolean isDisposeInProgress = ApplicationManager.getApplication().isDisposeInProgress(); // ignore caret placing when exiting
    Editor activeEditor = isDisposeInProgress ? null : CommonDataKeys.EDITOR.getData(dataContext);

    // when virtual space enabled, we can strip whitespace anywhere
    boolean isVirtualSpaceEnabled = activeEditor == null || activeEditor.getSettings().isVirtualSpace();

    int caretLine = activeEditor == null ? -1 : activeEditor.getCaretModel().getLogicalPosition().line;

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null) return;

    String stripTrailingSpaces = settings.getStripTrailingSpaces();
    final boolean doStrip = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);

    final boolean inChangedLinesOnly = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
    if (!inChangedLinesOnly || !doStrip || isVirtualSpaceEnabled) caretLine = -1;

    ((DocumentImpl)document).clearLineModificationFlagsExcept(caretLine);
  }

  public static boolean stripIfNotCurrentLine(Document document, boolean inChangedLinesOnly) {
    if (document instanceof DocumentWindow) {
      document = ((DocumentWindow)document).getDelegate();
    }
    if (!(document instanceof DocumentImpl)) {
      return true;
    }
    DataContext dataContext = DataManager.getInstance().getDataContext(IdeFocusManager.getGlobalInstance().getFocusOwner());
    boolean isDisposeInProgress = ApplicationManager.getApplication().isDisposeInProgress(); // ignore caret placing when exiting
    Editor activeEditor = isDisposeInProgress ? null : CommonDataKeys.EDITOR.getData(dataContext);

    // when virtual space enabled, we can strip whitespace anywhere
    boolean isVirtualSpaceEnabled = activeEditor == null || activeEditor.getSettings().isVirtualSpace();

    VisualPosition visualCaret = activeEditor == null ? null : activeEditor.getCaretModel().getVisualPosition();
    int caretLine = activeEditor == null ? -1 : activeEditor.getCaretModel().getLogicalPosition().line;
    int caretOffset = activeEditor == null ? -1 : activeEditor.getCaretModel().getOffset();

    final Project project = activeEditor == null ? null : activeEditor.getProject();
    boolean markAsNeedsStrippingLater = ((DocumentImpl)document).stripTrailingSpaces(project, inChangedLinesOnly, isVirtualSpaceEnabled,
                                                                                     caretLine, caretOffset);

    if (!ShutDownTracker.isShutdownHookRunning() && activeEditor != null) {
      activeEditor.getCaretModel().moveToVisualPosition(visualCaret);
    }
    return !markAsNeedsStrippingLater;
  }

  public void documentDeleted(@NotNull Document doc) {
    myDocumentsToStripLater.remove(doc);
  }

  @Override
  public void unsavedDocumentsDropped() {
    myDocumentsToStripLater.clear();
  }
}
