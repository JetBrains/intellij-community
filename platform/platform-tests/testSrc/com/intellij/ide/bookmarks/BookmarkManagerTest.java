// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmarks;

import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.bookmark.BookmarkGroup;
import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.bookmark.GutterLineBookmarkRenderer;
import com.intellij.ide.bookmark.LineBookmark;
import com.intellij.ide.bookmark.providers.InvalidBookmark;
import com.intellij.ide.bookmark.providers.LineBookmarkImpl;
import com.intellij.ide.bookmark.providers.LineBookmarkProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.PlatformTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BookmarkManagerTest extends AbstractEditorTest {
  private static final int DEFAULT_SWITCH_CYCLES = 2;
  
  private static final String CLASS_HEADER = """
    class X {
        public int xxx;
    """;
  
  private static final String USAGE1_METHOD = """
        void usage1() {
            System.out.println("Usage 1 " + xxx);
        }
    """;
  
  private static final String USAGE2_METHOD = """
        void usage2() {
            System.out.println("Usage 2 " + xxx);
        }
    """;
  
  private static final String CLASS_FOOTER = "}";
  
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
      """
        public class Test {
            public void test() {
                int i = 1;
            }
        }""";
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
      """
        public class Test {
            public void test() {
                int i = 1;
            }
        }""";
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
      """
        public class Test {
            public void test() {
                int i = 1;
                int j = 1;
            }
        }""";
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
      """
        public class Test {
            public void test() {
                int i = 1;
                int j = 1;
            }
        }""";
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
      """
        public class Test {
            public void test() {
                int i = 1;
            }
        }""";
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
    getManager().add(LineBookmarkProvider.Util.find(getProject()).createBookmark(file, line), BookmarkType.DEFAULT);
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

  public void testBranchSwitch_BookmarksInvalidatedAndRestored() {
    init(BranchKind.BRANCH1.getContent(), PlainTextFileType.INSTANCE);
    VirtualFile file = getFile().getVirtualFile();
    LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());
    Document document = FileDocumentManager.getInstance().getDocument(file);

    String classLine = LineBookmarkProvider.Util.readLineText(document, 0);
    String usage1Line = LineBookmarkProvider.Util.readLineText(document, 3);
    String usage2Line = LineBookmarkProvider.Util.readLineText(document, 7);

    addBookmark(file, 0);
    addBookmark(file, 3);
    addBookmark(file, 7);
    assertEquals(3, getManager().getBookmarks().size());

    runSwitchSequence(new BranchKind[]{BranchKind.BRANCH2, BranchKind.BRANCH3, BranchKind.BRANCH1}, DEFAULT_SWITCH_CYCLES, provider, classLine, usage1Line, usage2Line);
  }

  public void testBranchSwitch_BookmarksWithoutExpectedText() {
    init(BranchKind.BRANCH1.getContent(), PlainTextFileType.INSTANCE);
    VirtualFile file = getFile().getVirtualFile();
    LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());
    Document document = FileDocumentManager.getInstance().getDocument(file);

    String classLine = LineBookmarkProvider.Util.readLineText(document, 0);
    String usage1Line = LineBookmarkProvider.Util.readLineText(document, 3);
    String usage2Line = LineBookmarkProvider.Util.readLineText(document, 7);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> 
      getEditor().getDocument().setText(BranchKind.BRANCH1.getContent()));
    getManager().remove();
    BookmarkGroup group = getManager().addGroup("Test", true);
    if (group == null) group = getManager().getGroup("Test");
    assertNotNull(group);
    group.add(new LineBookmarkImpl(provider, file, 0), BookmarkType.DEFAULT, null);
    group.add(new LineBookmarkImpl(provider, file, 3), BookmarkType.DEFAULT, null);
    group.add(new LineBookmarkImpl(provider, file, 7), BookmarkType.DEFAULT, null);
    assertEquals(3, getManager().getBookmarks().size());

    runSwitchSequence(new BranchKind[]{BranchKind.BRANCH2, BranchKind.BRANCH3, BranchKind.BRANCH1}, 
                      DEFAULT_SWITCH_CYCLES, provider, classLine, usage1Line, usage2Line);
  }

  private enum BranchKind {
    BRANCH1(CLASS_HEADER + "\n" + USAGE1_METHOD + "\n" + USAGE2_METHOD + "\n" + CLASS_FOOTER),
    BRANCH2(CLASS_HEADER + "\n" + USAGE1_METHOD + "\n" + CLASS_FOOTER),
    BRANCH3(CLASS_HEADER + "\n" + USAGE2_METHOD + "\n" + CLASS_FOOTER);

    private final String content;

    BranchKind(String content) {
      this.content = content;
    }

    private String getContent() {
      return content;
    }
  }

  private void runSwitchSequence(BranchKind[] order,
                                 int cycles,
                                 LineBookmarkProvider provider,
                                 String classLine,
                                 String usage1Line,
                                 String usage2Line) {
    for (int i = 0; i < cycles; i++) {
      for (BranchKind branch : order) {
        WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().setText(branch.getContent()));
        assertBranchState(branch, classLine, usage1Line, usage2Line, provider);
      }
    }
  }

  private void assertBranchState(BranchKind branch,
                                 String classLine,
                                 String usage1Line,
                                 String usage2Line,
                                 LineBookmarkProvider provider) {
    boolean classValid = true;
    boolean usage1Valid = branch != BranchKind.BRANCH3;
    boolean usage2Valid = branch != BranchKind.BRANCH2;
    
    List<Bookmark> bookmarks = getManager().getBookmarks();
    assertEquals(3, bookmarks.size());
    
    int expectedValidCount = 1 + (usage1Valid ? 1 : 0) + (usage2Valid ? 1 : 0);
    long validCount = bookmarks.stream()
      .filter(LineBookmark.class::isInstance)
      .count();
    assertEquals(expectedValidCount, (int)validCount);
    List<Integer> lines = bookmarks.stream()
      .filter(LineBookmark.class::isInstance)
      .map(b -> ((LineBookmark)b).getLine())
      .toList();
    assertTrue(lines.contains(0));
    assertTrue(lines.contains(3));
    if (usage1Valid && usage2Valid) {
      assertTrue(lines.contains(7));
    }
    
    if (branch != BranchKind.BRANCH1) {
      assertInvalidVisibleInTree(provider);
    }
    
    assertGutterState(classLine, usage1Line, usage2Line, classValid, usage1Valid, usage2Valid);
  }

  private Bookmark findBookmarkByLineText(List<Bookmark> bookmarks, String lineText) {
    for (Bookmark b : bookmarks) {
      String text = b.getAttributes().get("lineText");
      if (Objects.equals(text, lineText)) return b;
    }
    return null;
  }

  private void assertInvalidVisibleInTree(LineBookmarkProvider provider) {
    List<AbstractTreeNode<?>> nodes = new ArrayList<>();
    for (Bookmark b : getManager().getBookmarks()) {
      nodes.add(b.createNode());
    }
    List<AbstractTreeNode<?>> prepared = provider.prepareGroup(nodes);

    boolean foundInvalid = false;
    for (AbstractTreeNode<?> n : prepared) {
      if (n.getValue() instanceof InvalidBookmark) foundInvalid = true;
      for (AbstractTreeNode<?> child : n.getChildren()) {
        if (child.getValue() instanceof InvalidBookmark) foundInvalid = true;
      }
    }
    assertTrue("Invalid bookmark should be visible in the tree (directly or grouped)", foundInvalid);
  }

  public void testBookmarkGroupingWithValidAndInvalidBookmarks() {
    init(BranchKind.BRANCH1.getContent(), PlainTextFileType.INSTANCE);
    VirtualFile file = getFile().getVirtualFile();
    LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

    addBookmark(file, 0);
    addBookmark(file, 3);
    addBookmark(file, 7);
    assertEquals(3, getManager().getBookmarks().size());

    WriteCommandAction.writeCommandAction(getProject()).run(() -> 
      getEditor().getDocument().setText(BranchKind.BRANCH2.getContent()));

    List<Bookmark> bookmarks = getManager().getBookmarks();
    assertEquals(3, bookmarks.size());
    
    long validCount = bookmarks.stream().filter(b -> b instanceof LineBookmark).count();
    long invalidCount = bookmarks.stream().filter(b -> b instanceof InvalidBookmark).count();
    assertEquals(2, validCount);
    assertEquals(1, invalidCount);

    List<AbstractTreeNode<?>> nodes = new ArrayList<>();
    for (Bookmark b : bookmarks) {
      nodes.add(b.createNode());
    }
    List<AbstractTreeNode<?>> prepared = provider.prepareGroup(nodes);

    List<Bookmark> seenBookmarks = new ArrayList<>();
    
    for (AbstractTreeNode<?> node : prepared) {
      Object value = node.getValue();
      
      if (value instanceof Bookmark) {
        seenBookmarks.add((Bookmark) value);
      }
      
      for (AbstractTreeNode<?> child : node.getChildren()) {
        Object childValue = child.getValue();
        if (childValue instanceof Bookmark) {
          seenBookmarks.add((Bookmark)childValue);
        }
      }
    }

    long uniqueCount = seenBookmarks.stream().distinct().count();
    assertEquals(bookmarks.size(), uniqueCount);
    assertEquals(3, seenBookmarks.size());
    
    long visibleValid = seenBookmarks.stream().filter(b -> b instanceof LineBookmark).count();
    long visibleInvalid = seenBookmarks.stream().filter(b -> b instanceof InvalidBookmark).count();
    assertEquals(2, visibleValid);
    assertEquals(1, visibleInvalid);
    
    dispatchGutterUpdates();
    for (Bookmark bookmark : bookmarks) {
      boolean isValid = bookmark instanceof LineBookmark;
      boolean hasGutter = hasBookmarkGutter(bookmark);
      if (isValid) {
        assertTrue(hasGutter);
      }
      else {
        assertFalse(hasGutter);
      }
    }
  }

  private void assertGutterState(String classLine,
                                 String usage1Line,
                                 String usage2Line,
                                 boolean classValid,
                                 boolean usage1Valid,
                                 boolean usage2Valid) {
    List<Bookmark> bookmarks = getManager().getBookmarks();
    Bookmark classBookmark = findBookmarkByLineText(bookmarks, classLine);
    Bookmark usage1 = findBookmarkByLineText(bookmarks, usage1Line);
    Bookmark usage2 = findBookmarkByLineText(bookmarks, usage2Line);
    assertNotNull(classBookmark);
    assertNotNull(usage1);
    assertNotNull(usage2);
    dispatchGutterUpdates();
    assertEquals(classValid, hasBookmarkGutter(classBookmark));
    assertEquals(usage1Valid, hasBookmarkGutter(usage1));
    assertEquals(usage2Valid, hasBookmarkGutter(usage2));
  }

  private void dispatchGutterUpdates() {
    if (getEditor().getGutter() instanceof EditorGutterComponentEx gutter) {
      gutter.revalidateMarkup();
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  private boolean hasBookmarkGutter(Bookmark bookmark) {
    int line;
    if (bookmark instanceof LineBookmark) {
      line = ((LineBookmark)bookmark).getLine();
    } else {
      String lineStr = bookmark.getAttributes().get("line");
      if (lineStr == null) {
        line = -1;
      } else {
        try {
          line = Integer.parseInt(lineStr);
        }
        catch (NumberFormatException ignored) {
          line = -1;
        }
      }
    }
    if (line < 0 || !(getEditor().getGutter() instanceof EditorGutterComponentEx gutter)) {
      return false;
    }
    return gutter.getGutterRenderers(line).stream()
      .filter(GutterLineBookmarkRenderer.class::isInstance)
      .map(GutterLineBookmarkRenderer.class::cast)
      .anyMatch(renderer -> renderer.getBookmark().equals(bookmark));
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

  /**
   * Stress test simulating realistic branch switching scenario:
   * - Multiple files (30 files)
   * - Many bookmarks (100 total, ~3-4 per file)
   * - Branch switches with content changes (some lines moved, some removed)
   * - Multiple rapid switches to test performance and correctness
   * 
   * Tests the performance of:
   * - BookmarkContextProvider.mergeState() with many bookmarks
   * - LineBookmarkProvider.Util.findLineByText() for moved lines
   * - BookmarkContextProvider.saveContext() with file cache
   */
  public void testBranchSwitchStressWithManyBookmarksAndFiles() throws IOException {
    final int FILE_COUNT = 30;
    final int BOOKMARKS_PER_FILE = 3;
    final int TOTAL_BOOKMARKS = FILE_COUNT * BOOKMARKS_PER_FILE;
    final int SWITCH_CYCLES = 5;

    // Create 30 files with different content
    List<VirtualFile> files = new ArrayList<>();
    for (int i = 0; i < FILE_COUNT; i++) {
      final int fileIndex = i;
      String content = generateFileContent(fileIndex, 50); // 50 lines per file
      VirtualFile file = WriteAction.compute(() -> {
        VirtualFile f = getSourceRoot().createChildData(null, "File" + fileIndex + ".txt");
        VfsUtil.saveText(f, content);
        return f;
      });
      files.add(file);
    }

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    
    // Add 3 bookmarks per file (beginning, middle, end)
    LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());
    assertNotNull(provider);
    
    for (VirtualFile file : files) {
      addBookmark(file, 5);   // Beginning bookmark
      addBookmark(file, 25);  // Middle bookmark  
      addBookmark(file, 45);  // End bookmark
    }
    
    assertEquals(TOTAL_BOOKMARKS, getManager().getBookmarks().size());
    
    // Capture expected line texts for verification
    Map<VirtualFile, List<String>> expectedTexts = new HashMap<>();
    for (VirtualFile file : files) {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      List<String> texts = List.of(
        LineBookmarkProvider.Util.readLineText(document, 5),
        LineBookmarkProvider.Util.readLineText(document, 25),
        LineBookmarkProvider.Util.readLineText(document, 45)
      );
      expectedTexts.put(file, texts);
    }

    long startTime = System.currentTimeMillis();
    
    // Simulate branch switching with performance tracking
    for (int cycle = 0; cycle < SWITCH_CYCLES; cycle++) {
      // Branch 2: Remove lines 20-30 from each file (middle bookmark at line 25 becomes invalid)
      switchToBranch2(files);
      
      List<Bookmark> bookmarks = getManager().getBookmarks();
      assertEquals("Cycle " + cycle + ": Should maintain all bookmarks", TOTAL_BOOKMARKS, bookmarks.size());
      
      long validCount = bookmarks.stream().filter(b -> b instanceof LineBookmark).count();
      long invalidCount = bookmarks.stream().filter(b -> b instanceof InvalidBookmark).count();
      
      // In branch 2, middle bookmarks (line 25 with method content) should be invalidated
      // because lines 20-30 are removed
      assertEquals("Cycle " + cycle + ": Should have invalid bookmarks in branch 2", 
                   FILE_COUNT, invalidCount);
      assertEquals("Cycle " + cycle + ": Should have valid bookmarks in branch 2",
                   FILE_COUNT * 2, validCount);

      // Branch 3: Method at line 25 moved to line 35, should be found by text matching
      switchToBranch3(files);
      
      bookmarks = getManager().getBookmarks();
      assertEquals("Cycle " + cycle + ": Should maintain all bookmarks", TOTAL_BOOKMARKS, bookmarks.size());
      
      validCount = bookmarks.stream().filter(b -> b instanceof LineBookmark).count();
      // All bookmarks should be valid: line 5 unchanged, line 25 moved to 35, line 45 unchanged
      assertEquals("Cycle " + cycle + ": Should restore all bookmarks in branch 3", 
                   TOTAL_BOOKMARKS, validCount);

      // Back to original branch
      restoreOriginalContent(files, expectedTexts);
      
      bookmarks = getManager().getBookmarks();
      assertEquals("Cycle " + cycle + ": Should maintain all bookmarks", TOTAL_BOOKMARKS, bookmarks.size());
      
      validCount = bookmarks.stream().filter(b -> b instanceof LineBookmark).count();
      assertEquals("Cycle " + cycle + ": All bookmarks should be valid in original branch",
                   TOTAL_BOOKMARKS, validCount);
      
      // Verify bookmarks are at correct lines
      verifyBookmarkLines(files, List.of(5, 25, 45));
    }
    
    long duration = System.currentTimeMillis() - startTime;
    
    // Performance assertion: 30 files × 3 bookmarks × 3 switches × 5 cycles = 1350 operations
    // Should complete in reasonable time (< 10 seconds)
    assertTrue("Stress test took too long: " + duration + "ms (expected < 10000ms)", 
               duration < 10000);
    
    System.out.println("Stress test completed in " + duration + "ms for " + 
                       (TOTAL_BOOKMARKS * SWITCH_CYCLES * 3) + " bookmark operations");
  }

  private String generateFileContent(int fileIndex, int lineCount) {
    StringBuilder sb = new StringBuilder();
    sb.append("// File ").append(fileIndex).append("\n");
    sb.append("class File").append(fileIndex).append(" {\n");
    
    for (int i = 0; i < lineCount - 4; i++) {
      if (i == 4) {
        sb.append("    public static final int CONSTANT_").append(fileIndex).append(" = ").append(fileIndex).append(";\n");
      }
      else if (i == 24) {
        sb.append("    public void method").append(fileIndex).append("() {\n");
      }
      else if (i == 25) {
        sb.append("        System.out.println(\"Method ").append(fileIndex).append("\");\n");
      }
      else if (i == 26) {
        sb.append("    }\n");
      }
      else if (i == 44) {
        sb.append("    private int field").append(fileIndex).append(";\n");
      }
      else {
        sb.append("    // Line ").append(i).append(" in file ").append(fileIndex).append("\n");
      }
    }
    
    sb.append("}\n");
    return sb.toString();
  }

  private void switchToBranch2(List<VirtualFile> files) throws IOException {
    // Branch 2: Middle section containing method (lines 23-27) removed from each file
    for (int i = 0; i < files.size(); i++) {
      VirtualFile file = files.get(i);
      String content = generateFileContentBranch2(i);
      WriteAction.run(() -> VfsUtil.saveText(file, content));
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  private String generateFileContentBranch2(int fileIndex) {
    // Branch 2: Method section (lines 23-27) removed, so line 25 bookmark (method body) becomes invalid
    // Other lines stay at same positions
    StringBuilder sb = new StringBuilder();
    sb.append("// File ").append(fileIndex).append("\n");
    sb.append("class File").append(fileIndex).append(" {\n");
    
    for (int i = 0; i < 46; i++) {
      if (i == 4) {
        sb.append("    public static final int CONSTANT_").append(fileIndex).append(" = ").append(fileIndex).append(";\n");
      }
      else if (i == 44) {
        sb.append("    private int field").append(fileIndex).append(";\n");
      }
      else if (i < 23 || i > 27) {
        sb.append("    // Line ").append(i).append(" in file ").append(fileIndex).append("\n");
      }
    }
    
    sb.append("}\n");
    return sb.toString();
  }

  private void switchToBranch3(List<VirtualFile> files) throws IOException {
    // Branch 3: Method moved from line 25 to line 35
    for (int i = 0; i < files.size(); i++) {
      VirtualFile file = files.get(i);
      String content = generateFileContentBranch3(i);
      WriteAction.run(() -> VfsUtil.saveText(file, content));
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  private String generateFileContentBranch3(int fileIndex) {
    // Branch 3: Method body at line 25 moved to line 35
    StringBuilder sb = new StringBuilder();
    sb.append("// File ").append(fileIndex).append("\n");
    sb.append("class File").append(fileIndex).append(" {\n");
    
    for (int i = 0; i < 50; i++) {
      if (i == 4) {
        sb.append("    public static final int CONSTANT_").append(fileIndex).append(" = ").append(fileIndex).append(";\n");
      }
      else if (i == 24) {
        sb.append("    public void method").append(fileIndex).append("() {\n");
      }
      else if (i == 25) {
        // Original println location - now just a comment
        sb.append("        // Method body moved\n");
      }
      else if (i == 26) {
        sb.append("    }\n");
      }
      else if (i == 35) {
        // Method body moved here
        sb.append("        System.out.println(\"Method ").append(fileIndex).append("\");\n");
      }
      else if (i == 44) {
        sb.append("    private int field").append(fileIndex).append(";\n");
      }
      else {
        sb.append("    // Line ").append(i).append(" in file ").append(fileIndex).append("\n");
      }
    }
    
    sb.append("}\n");
    return sb.toString();
  }

  private void restoreOriginalContent(List<VirtualFile> files, Map<VirtualFile, List<String>> expectedTexts) throws IOException {
    for (int i = 0; i < files.size(); i++) {
      VirtualFile file = files.get(i);
      String content = generateFileContent(i, 50);
      WriteAction.run(() -> VfsUtil.saveText(file, content));
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  private void verifyBookmarkLines(List<VirtualFile> files, List<Integer> expectedLines) {
    for (VirtualFile file : files) {
      List<Integer> actualLines = getManager().getBookmarks().stream()
        .filter(b -> b instanceof LineBookmark)
        .map(b -> (LineBookmark)b)
        .filter(b -> b.getFile().equals(file))
        .map(LineBookmark::getLine)
        .sorted()
        .toList();
      
      assertEquals("File " + file.getName() + " should have bookmarks at expected lines",
                   expectedLines, actualLines);
    }
  }
}
