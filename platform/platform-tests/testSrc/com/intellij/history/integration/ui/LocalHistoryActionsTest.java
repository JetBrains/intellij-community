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

package com.intellij.history.integration.ui;

import com.intellij.history.integration.TestVirtualFile;
import com.intellij.history.integration.ui.actions.LocalHistoryAction;
import com.intellij.history.integration.ui.actions.ShowHistoryAction;
import com.intellij.history.integration.ui.actions.ShowSelectionHistoryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.containers.UtilKt.stream;

public class LocalHistoryActionsTest extends LocalHistoryUITestCase {
  VirtualFile f;
  Editor editor;
  Document document;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = createChildData(myRoot, "f.txt");

    document = FileDocumentManager.getInstance().getDocument(f);
    document.setText("foo");

    editor = getEditorFactory().createEditor(document, myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getEditorFactory().releaseEditor(editor);
    }
    finally {
      super.tearDown();
    }
  }

  private static EditorFactory getEditorFactory() {
    return EditorFactory.getInstance();
  }

  public void testShowHistoryAction() {
    ShowHistoryAction a = new ShowHistoryAction();
    assertStatus(a, myRoot, true);
    assertStatus(a, f, true);
    assertStatus(a, null, false);

    assertStatus(a, createChildData(myRoot, "f.hprof"), false);
    assertStatus(a, createChildData(myRoot, "f.xxx"), false);
  }

  public void testLocalHistoryActionDisabledWithoutProject() {
    LocalHistoryAction a = new LocalHistoryAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
      }
    };
    assertStatus(a, myRoot, myProject, true);
    assertStatus(a, myRoot, null, false);
  }
  
  public void testShowHistoryActionIsDisabledForMultipleSelection() {
    ShowHistoryAction a = new ShowHistoryAction();
    assertStatus(a, new VirtualFile[] {f, new TestVirtualFile("ff")}, myProject, false);
  }

  public void testShowSelectionHistoryActionForSelection() {
    editor.getSelectionModel().setSelection(0, 2);

    ShowSelectionHistoryAction a = new ShowSelectionHistoryAction();
    AnActionEvent e = createEventFor(a, new VirtualFile[] {f}, myProject);
    a.update(e);

    assertTrue(e.getPresentation().isEnabled());

    assertEquals("Show History for Selection", e.getPresentation().getText());
  }

  public void testShowSelectionHistoryActionIsDisabledForNonFiles() {
    ShowSelectionHistoryAction a = new ShowSelectionHistoryAction();
    assertStatus(a, myRoot, false);
    assertStatus(a, null, false);
  }

  public void testShowSelectionHistoryActionIsDisabledForEmptySelection() {
    ShowSelectionHistoryAction a = new ShowSelectionHistoryAction();
    assertStatus(a, f, false);
  }

  private void assertStatus(AnAction a, VirtualFile f, boolean isEnabled) {
    assertStatus(a, f, myProject, isEnabled);
  }

  private void assertStatus(AnAction a, VirtualFile f, Project p, boolean isEnabled) {
    VirtualFile[] files = f == null ? null : new VirtualFile[]{f};
    assertStatus(a, files, p, isEnabled);
  }

  private void assertStatus(AnAction a, VirtualFile[] files, Project p, boolean isEnabled) {
    AnActionEvent e = createEventFor(a, files, p);
    a.update(e);
    assertEquals(isEnabled, e.getPresentation().isEnabled());
  }

  private AnActionEvent createEventFor(AnAction a, final VirtualFile[] files, final Project p) {
    DataContext dc = new DataContext() {
      @Override
      @Nullable
      public Object getData(String id) {
        if (VcsDataKeys.VIRTUAL_FILE_STREAM.is(id)) return stream(files);
        if (CommonDataKeys.EDITOR.is(id)) return editor;
        if (CommonDataKeys.PROJECT.is(id)) return p;
        return null;
      }
    };
    return AnActionEvent.createFromAnAction(a, null, "", dc);
  }
}
