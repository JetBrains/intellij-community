/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.AbstractEditorProcessingOnDocumentModificationTest;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.TestFileType;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 12/27/10 1:43 PM
 */
public class BookmarkManagerTest extends AbstractEditorProcessingOnDocumentModificationTest {
  private final List<Bookmark> myBookmarks = new ArrayList<Bookmark>();
  
  @Override
  protected void tearDown() throws Exception {
    for (Bookmark bookmark : myBookmarks) {
      getManager().removeBookmark(bookmark);
    }
    myBookmarks.clear();
    super.tearDown();
  }

  public void testWholeTextReplace() throws IOException {
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
    
    myEditor.getDocument().setText(text);
    List<Bookmark> bookmarksAfter = getManager().getValidBookmarks();
    assertEquals(1, bookmarksAfter.size());
    assertSame(bookmarksBefore.get(0), bookmarksAfter.get(0));
    for (Bookmark bookmark : bookmarksAfter) {
      checkBookmarkNavigation(bookmark);
    }
  }
  
  public void testBookmarkLineRemove() throws IOException {
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
  
  public void testBookmarkIsSavedAfterRemoteChange() throws IOException {
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "    }\n" +
      "}";
    init(text, TestFileType.TEXT);
    addBookmark(2);
    
    myEditor.getDocument().setText("111\n222" + text + "333");
    List<Bookmark> bookmarks = getManager().getValidBookmarks();
    assertEquals(1, bookmarks.size());
    Bookmark bookmark = bookmarks.get(0);
    assertEquals(3, bookmark.getLine());
    checkBookmarkNavigation(bookmark);
  }

  public void testBookmarkManagerDoesNotHardReferenceDocuments() throws IOException {
    @NonNls String text =
      "public class Test {\n" +
      "}";

    myVFile = getSourceRoot().createChildData(null, getTestName(false) + ".txt");
    VfsUtil.saveText(myVFile, text);

    Bookmark bookmark = getManager().addTextBookmark(myVFile, 1, "xxx");
    assertNotNull(bookmark);
    LeakHunter.checkLeak(getManager(), Document.class);

    Document document = FileDocumentManager.getInstance().getDocument(myVFile);
    assertNotNull(document);

    document.insertString(0, "line 0\n");
    assertEquals(2, bookmark.getLine());

    myEditor = createEditor(myVFile);
    checkBookmarkNavigation(bookmark);
  }
  
  private void addBookmark(int line) {
    Bookmark bookmark = getManager().addTextBookmark(getFile().getVirtualFile(), line, "");
    myBookmarks.add(bookmark);
  }
  
  private static BookmarkManager getManager() {
    return BookmarkManager.getInstance(getProject());
  }

  @Override
  public Object getData(String dataId) {
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
}
