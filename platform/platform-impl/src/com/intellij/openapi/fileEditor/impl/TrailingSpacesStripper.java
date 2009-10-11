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

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.util.text.CharArrayUtil;

public final class TrailingSpacesStripper extends FileDocumentManagerAdapter {
  public void beforeDocumentSaving(final Document document) {
    if (!document.isWritable()) return;

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null) return;

    final boolean doStrip = !settings.getStripTrailingSpaces().equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    final boolean ensureEOL = settings.isEnsureNewLineAtEOF();
    if (!doStrip && !ensureEOL) return;

    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      public void run() {
        if (doStrip) {
          final boolean inChangedLinesOnly = !settings.getStripTrailingSpaces().equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
          ((DocumentEx)document).stripTrailingSpaces(inChangedLinesOnly);
        }

        if (ensureEOL) {
          final int lines = document.getLineCount();
          if (lines > 0) {
            int start = document.getLineStartOffset(lines - 1);
            int end = document.getLineEndOffset(lines - 1);
            if (start != end) {
              CharSequence content = document.getCharsSequence();
              if (CharArrayUtil.containsOnlyWhiteSpaces(content.subSequence(start, end))) {
                document.deleteString(start, end);
              }
              else {
                document.insertString(end, "\n");
              }
            }
          }
        }
      }
    });
  }
}
