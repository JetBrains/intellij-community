/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.history.integration;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.history.core.revisions.CurrentRevision;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ActionsTest extends IntegrationTestCase {
  public void testSavingDocumentBeforeAndAfterAction() throws Exception {
    VirtualFile f = createFile("f.txt", "file1");
    loadContent(f);
    setContent(f, "file2");

    setDocumentTextFor(f, "doc1");

    LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
    setDocumentTextFor(f, "doc2");
    a.finish();

    List<Revision> rr = getRevisionsFor(f);
    assertEquals("""
                   current: doc2
                   action: doc1
                   null: file2
                   null: file1
                   External change: null""", getNameAndOldContent(rr));
  }

  /**
   * <strong>This is very important test.</strong>
   * Almost all actions are performed inside surrounding command.
   * Therefore we have to correctly handle such situations.
   */
  public void testActionInsideCommand() throws Exception {
    final VirtualFile f = createFile("f.txt");
    setContent(f, "initial");
    setDocumentTextFor(f, "doc1");

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
      setDocumentTextFor(f, "doc2");
      a.finish();
    }, "command", null);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals("""
                   current: doc2
                   command: doc1
                   null: initial
                   null:\s
                   External change: null""", getNameAndOldContent(rr));
  }

  public void testActionInsideCommandSurroundedWithSomeChanges() throws Exception {
    // see testActionInsideCommand comment
    final VirtualFile f = createFile("f.txt");

    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() {
        setContent(f, "file");
        setDocumentTextFor(f, "doc1");

        LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
        setDocumentTextFor(f, "doc2");
        a.finish();

        saveDocument(f);
        setContent(f, "doc3");
      }
    }, "command", null);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals("""
                   current: doc3
                   command: doc1
                   null:\s
                   External change: null""", getNameAndOldContent(rr));
  }

  private static @NotNull String getNameAndOldContent(@NotNull List<Revision> revisions) {
    return StringUtil.join(revisions, revision -> {
      String name = revision instanceof CurrentRevision ? "current" : revision.getChangeSetName();
      Entry entry = revision.findEntry();
      String content = entry == null ? "null" : getContentAsString(entry);
      return name + ": " + content;
    }, "\n");
  }

  private static void saveDocument(VirtualFile f) {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    Document document = dm.getDocument(f);
    assertNotNull(f.getPath(), document);
    dm.saveDocument(document);
  }
}