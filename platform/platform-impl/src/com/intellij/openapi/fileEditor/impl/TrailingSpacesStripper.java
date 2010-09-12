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

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.TestOnly;

import java.util.Set;

public final class TrailingSpacesStripper {
  private final Set<DocumentEx> myDocumentsToStripLater = new THashSet<DocumentEx>();

  public TrailingSpacesStripper(MessageBus bus) {
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void beforeAllDocumentsSaving() {
        Set<DocumentEx> documentsToStrip = new THashSet<DocumentEx>(myDocumentsToStripLater);
        myDocumentsToStripLater.clear();
        for (DocumentEx documentEx : documentsToStrip) {
          strip(documentEx);
        }
      }

      @Override
      public void beforeDocumentSaving(Document document) {
        strip(document);
      }
    });
  }

  private void strip(final Document document) {
    if (!document.isWritable()) return;

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null) return;

    String stripTrailingSpaces = settings.getStripTrailingSpaces();
    final boolean doStrip = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    final boolean ensureEOL = settings.isEnsureNewLineAtEOF();

    if (doStrip) {
      final boolean inChangedLinesOnly = !stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
      DocumentEx ex = (DocumentEx)document;
      boolean success = ex.stripTrailingSpaces(inChangedLinesOnly);
      if (!success) {
        myDocumentsToStripLater.add(ex);
      }
    }

    final int lines = document.getLineCount();
    if (ensureEOL && lines > 0) {
      final int start = document.getLineStartOffset(lines - 1);
      final int end = document.getLineEndOffset(lines - 1);
      if (start != end) {
        final CharSequence content = document.getCharsSequence();
        ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(document, null) {
          public void run() {
            CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
              public void run() {
                if (CharArrayUtil.containsOnlyWhiteSpaces(content.subSequence(start, end))) {
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

  @TestOnly
  public void dropAll() {
    myDocumentsToStripLater.clear();
  }
}
