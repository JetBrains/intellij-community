/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.bookmarks;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.TestFileType;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.ComponentAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 */
public class BookmarkManagerTest extends AbstractEditorTest {
  private final List<Bookmark> myBookmarks = new ArrayList<>();
  
  @Override
  protected void tearDown() throws Exception {
    for (Bookmark bookmark : myBookmarks) {
      getManager().removeBookmark(bookmark);
    }
    myBookmarks.clear();
    super.tearDown();
  }

  public void testWholeTextReplace() {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "    }\n" +
      "}";
    init(text, TestFileType.TEXT);

    addBookmark(2);
    List<Bookmark> bookmarksBefore = getManager().getValidBookmarks();
    assertEquals(1, bookmarksBefore.size());

    WriteCommandAction.writeCommandAction(getProject()).run(() -> myEditor.getDocument().setText(text));

    List<Bookmark> bookmarksAfter = getManager().getValidBookmarks();
    assertEquals(1, bookmarksAfter.size());
    assertSame(bookmarksBefore.get(0), bookmarksAfter.get(0));
    for (Bookmark bookmark : bookmarksAfter) {
      checkBookmarkNavigation(bookmark);
    }
  }
  
  public void testBookmarkLineRemove() {
    List<ComponentAdapter> adapters = getProject().getPicoContainer().getComponentAdaptersOfType(ChangeListManagerImpl.class);
    LOG.debug(adapters.size() + " adapters:");
    for (ComponentAdapter adapter : adapters) {
      LOG.debug(String.valueOf(adapter));
    }

    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "    }\n" +
      "}";
    init(text, TestFileType.TEXT);

    addBookmark(2);
    Document document = myEditor.getDocument();
    myEditor.getSelectionModel().setSelection(document.getLineStartOffset(2) - 1, document.getLineEndOffset(2));
    delete();
    assertTrue(getManager().getValidBookmarks().isEmpty());
  }

  public void testTwoBookmarksOnSameLine1() {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "        int j = 1;\n" +
      "    }\n" +
      "}";
    init(text, TestFileType.TEXT);

    addBookmark(2);
    addBookmark(3);
    List<Bookmark> bookmarksBefore = getManager().getValidBookmarks();
    assertEquals(2, bookmarksBefore.size());

    myEditor.getCaretModel().setCaretsAndSelections(
      Collections.singletonList(new CaretState(myEditor.visualToLogicalPosition(new VisualPosition(3, 0)), null, null)));
    backspace();

    List<Bookmark> bookmarksAfter = getManager().getValidBookmarks();
    assertEquals(1, bookmarksAfter.size());
    for (Bookmark bookmark : bookmarksAfter) {
      checkBookmarkNavigation(bookmark);
    }
  }
  public void testTwoBookmarksOnSameLine2() {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "        int j = 1;\n" +
      "    }\n" +
      "}";
    init(text, TestFileType.TEXT);

    addBookmark(2);
    addBookmark(3);
    List<Bookmark> bookmarksBefore = getManager().getValidBookmarks();
    assertEquals(2, bookmarksBefore.size());

    myEditor.getCaretModel().setCaretsAndSelections(
      Collections.singletonList(new CaretState(myEditor.visualToLogicalPosition(new VisualPosition(2, myEditor.getDocument().getLineEndOffset(2)+1)), null, null)));
    delete();

    List<Bookmark> bookmarksAfter = getManager().getValidBookmarks();
    assertEquals(1, bookmarksAfter.size());
    for (Bookmark bookmark : bookmarksAfter) {
      checkBookmarkNavigation(bookmark);
    }
    init(text, TestFileType.TEXT);
    myEditor.getCaretModel().setCaretsAndSelections(
      Collections.singletonList(
        new CaretState(myEditor.visualToLogicalPosition(new VisualPosition(2, myEditor.getDocument().getLineEndOffset(2))), null, null)));
    delete();
  }
  
  public void testBookmarkIsSavedAfterRemoteChange() {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "    }\n" +
      "}";
    init(text, TestFileType.TEXT);
    addBookmark(2);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> myEditor.getDocument().setText("111\n222" + text + "333"));

    List<Bookmark> bookmarks = getManager().getValidBookmarks();
    assertEquals(1, bookmarks.size());
    Bookmark bookmark = bookmarks.get(0);
    assertEquals(3, bookmark.getLine());
    checkBookmarkNavigation(bookmark);
  }

  public void testBookmarkManagerDoesNotHardReferenceDocuments() throws IOException {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "}";

    myVFile = WriteAction.compute(() -> {
      VirtualFile file = getSourceRoot().createChildData(null, getTestName(false) + ".txt");
      VfsUtil.saveText(file, text);
      return file;
    });
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    Bookmark bookmark = getManager().addTextBookmark(myVFile, 1, "xxx");
    assertNotNull(bookmark);
    LeakHunter.checkLeak(getManager(), Document.class, doc -> myVFile.equals(FileDocumentManager.getInstance().getFile(doc)));

    Document document = FileDocumentManager.getInstance().getDocument(myVFile);
    assertNotNull(document);
    PsiDocumentManager.getInstance(getProject()).getPsiFile(document); // create psi so that PsiChangeHandler won't leak

    WriteCommandAction.runWriteCommandAction(ourProject, () -> document.insertString(0, "line 0\n"));

    assertEquals(2, bookmark.getLine());

    myEditor = createEditor(myVFile);
    checkBookmarkNavigation(bookmark);
  }
  
  private Bookmark addBookmark(int line) {
    Bookmark bookmark = getManager().addTextBookmark(getFile().getVirtualFile(), line, "");
    myBookmarks.add(bookmark);
    return bookmark;
  }
  
  private static BookmarkManager getManager() {
    return BookmarkManager.getInstance(getProject());
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (dataId.equals(OpenFileDescriptor.NAVIGATE_IN_EDITOR.getName())) {
      return myEditor;
    }
    return super.getData(dataId);
  }

  private static void checkBookmarkNavigation(Bookmark bookmark) {
    int line = bookmark.getLine();
    int anotherLine = line;
    if (line > 0) {
      anotherLine--;
    }
    else {
      anotherLine++;
    }
    CaretModel caretModel = myEditor.getCaretModel();
    caretModel.moveToLogicalPosition(new LogicalPosition(anotherLine, 0));
    bookmark.navigate(true);
    assertEquals(line, caretModel.getLogicalPosition().line);
  }

  public void testAddAddDeleteFromMiddleMustMaintainIndicesContinuous() {
    init("x\nx\nx\nx\n", TestFileType.TEXT);
    Bookmark b0 = addBookmark(2);
    assertEquals(0, b0.index);
    Bookmark b1 = addBookmark(1);
    assertEquals(0, b1.index);
    assertEquals(1, b0.index);
    Bookmark b2 = addBookmark(0);
    assertEquals(0, b2.index);
    assertEquals(1, b1.index);
    assertEquals(2, b0.index);

    getManager().removeBookmark(b1);
    assertFalse(b1.isValid());
    assertEquals(0, b2.index);
    assertEquals(1, b0.index);

    List<Bookmark> list = getManager().getValidBookmarks();
    assertEquals(2, list.size());
    assertEquals(0, list.get(0).index);
    assertEquals(1, list.get(1).index);
  }
}
