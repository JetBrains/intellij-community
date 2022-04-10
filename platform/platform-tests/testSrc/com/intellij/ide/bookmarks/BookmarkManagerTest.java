// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmarks;

import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.bookmark.LineBookmark;
import com.intellij.ide.bookmark.providers.LineBookmarkProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LeakHunter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BookmarkManagerTest extends AbstractEditorTest {
  @Override
  protected void tearDown() throws Exception {
    try {
      for (Bookmark bookmark : getManager().getBookmarks()) {
        getManager().remove(bookmark);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  /*TODO:SAM
  public void testLoadState() {
    BookmarkManager manager = new BookmarkManager(getProject());
    manager.applyNewStateInTestMode(Collections.emptyList());
    assertThat(manager.getState()).isEqualTo("<BookmarkManager />");

    String text = "point me";
    init(text, TestFileType.TEXT);

    Bookmark bookmark = new Bookmark(getFile().getVirtualFile().getUrl(), 0, "description?");
    bookmark.setMnemonic('3');
    manager.applyNewStateInTestMode(Collections.singletonList(bookmark));
    assertThat(manager.getState()).isEqualTo("<BookmarkManager>\n" +
                                             "  <bookmark url=\"temp:///src/LoadState.txt\" description=\"description?\" line=\"0\" mnemonic=\"3\" />\n" +
                                             "</BookmarkManager>");
  }
  */

  public void testWholeTextReplace() {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "    }\n" +
      "}";
    init(text, PlainTextFileType.INSTANCE);

    addBookmark(2);
    List<Bookmark> bookmarksBefore = getManager().getBookmarks();
    assertEquals(1, bookmarksBefore.size());

    WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().setText(text));

    List<Bookmark> bookmarksAfter = getManager().getBookmarks();
    assertEquals(1, bookmarksAfter.size());
    assertSame(bookmarksBefore.get(0), bookmarksAfter.get(0));
    for (Bookmark bookmark : bookmarksAfter) {
      checkBookmarkNavigation(bookmark);
    }
  }

  public void testBookmarkLineRemove() {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int i = 1;\n" +
      "    }\n" +
      "}";
    init(text, PlainTextFileType.INSTANCE);

    addBookmark(2);
    Document document = getEditor().getDocument();
    getEditor().getSelectionModel().setSelection(document.getLineStartOffset(2) - 1, document.getLineEndOffset(2));
    delete();
    assertTrue(getManager().getBookmarks().isEmpty());
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
    init(text, PlainTextFileType.INSTANCE);

    addBookmark(2);
    addBookmark(3);
    List<Bookmark> bookmarksBefore = getManager().getBookmarks();
    assertEquals(2, bookmarksBefore.size());

    getEditor().getCaretModel().setCaretsAndSelections(
      Collections.singletonList(new CaretState(getEditor().visualToLogicalPosition(new VisualPosition(3, 0)), null, null)));
    backspace();

    List<Bookmark> bookmarksAfter = getManager().getBookmarks();
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
    init(text, PlainTextFileType.INSTANCE);

    addBookmark(2);
    addBookmark(3);
    List<Bookmark> bookmarksBefore = getManager().getBookmarks();
    assertEquals(2, bookmarksBefore.size());

    getEditor().getCaretModel().setCaretsAndSelections(
      Collections.singletonList(new CaretState(
        getEditor().visualToLogicalPosition(new VisualPosition(2, getEditor().getDocument().getLineEndOffset(2) + 1)), null, null)));
    delete();

    List<Bookmark> bookmarksAfter = getManager().getBookmarks();
    assertEquals(1, bookmarksAfter.size());
    for (Bookmark bookmark : bookmarksAfter) {
      checkBookmarkNavigation(bookmark);
    }
    init(text, PlainTextFileType.INSTANCE);
    getEditor().getCaretModel().setCaretsAndSelections(
      Collections.singletonList(
        new CaretState(getEditor().visualToLogicalPosition(new VisualPosition(2, getEditor().getDocument().getLineEndOffset(2))), null, null)));
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
    init(text, PlainTextFileType.INSTANCE);
    addBookmark(2);
    assertEquals(1, getManager().getBookmarks().size());

    WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().setText("111\n222" + text + "333"));

    List<Bookmark> bookmarks = getManager().getBookmarks();
    assertEquals(1, bookmarks.size());
    Bookmark bookmark = bookmarks.get(0);
    assertEquals(3, ((LineBookmark)bookmark).getLine());
    checkBookmarkNavigation(bookmark);
  }

  public void testBookmarkManagerDoesNotHardReferenceDocuments() throws IOException {
    @Language("JAVA")
    @NonNls String text =
      "public class Test {\n" +
      "}";

    setVFile(WriteAction.compute(() -> {
      VirtualFile file = getSourceRoot().createChildData(null, getTestName(false) + ".txt");
      VfsUtil.saveText(file, text);
      return file;
    }));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    addBookmark(getVFile(), 1);
    LeakHunter.checkLeak(getManager(), Document.class, doc -> getVFile().equals(FileDocumentManager.getInstance().getFile(doc)));

    Document document = FileDocumentManager.getInstance().getDocument(getVFile());
    assertNotNull(document);
    PsiDocumentManager.getInstance(getProject()).getPsiFile(document); // create psi so that PsiChangeHandler won't leak

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "line 0\n"));

    List<Bookmark> bookmarks = getManager().getBookmarks();
    assertEquals(1, bookmarks.size());
    Bookmark bookmark = bookmarks.get(0);
    assertEquals(2, ((LineBookmark)bookmark).getLine());

    setEditor(createEditor(getVFile()));
    checkBookmarkNavigation(bookmark);
  }

  private void addBookmark(int line) {
    addBookmark(getFile().getVirtualFile(), line);
  }

  private void addBookmark(VirtualFile file, int line) {
    getManager().add(LineBookmarkProvider.find(getProject()).createBookmark(file, line), BookmarkType.DEFAULT);
  }

  public void testBookmarkCreationMustNotLoadDocumentUnnecessarily() throws IOException {
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(PsiDocumentListener.TOPIC,
                                                                            (document, psiFile, project) -> fail("Document " + document + " was loaded unexpectedly"));

    @NonNls String text =
      "public class Test {\n" +
      "}";

    setVFile(WriteAction.compute(() -> {
      VirtualFile file = getSourceRoot().createChildData(null, getTestName(false) + ".txt");
      VfsUtil.saveText(file, text);
      return file;
    }));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    addBookmark(getVFile(), 1);
  }

  private BookmarksManager getManager() {
    return BookmarksManager.getInstance(getProject());
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
      return getEditor();
    }
    return super.getData(dataId);
  }

  private void checkBookmarkNavigation(Bookmark bookmark) {
    int line = ((LineBookmark)bookmark).getLine();
    int anotherLine = line;
    if (line > 0) {
      anotherLine--;
    }
    else {
      anotherLine++;
    }
    CaretModel caretModel = getEditor().getCaretModel();
    caretModel.moveToLogicalPosition(new LogicalPosition(anotherLine, 0));
    bookmark.navigate(true);
    assertEquals(line, caretModel.getLogicalPosition().line);
  }

  /*TODO:SAM
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

    getManager().remove(b1);
    assertFalse(b1.isValid());
    assertEquals(0, b2.index);
    assertEquals(1, b0.index);

    List<Bookmark> list = getManager().getBookmarks();
    assertEquals(2, list.size());
    assertEquals(0, list.get(0).index);
    assertEquals(1, list.get(1).index);
  }
  */
}
