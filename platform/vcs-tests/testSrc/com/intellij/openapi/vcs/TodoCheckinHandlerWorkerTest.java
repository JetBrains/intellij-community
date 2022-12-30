package com.intellij.openapi.vcs;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vcs.checkin.TodoCheckinHandlerWorker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.TodoItem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author irengrig
 */
public class TodoCheckinHandlerWorkerTest extends HeavyPlatformTestCase {
  private VirtualFile myChildData;
  private String myNewText;
  private VirtualFile myRootFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    WriteAction.run(() -> myRootFile = createTestProjectStructure());
  }

  public void testInEditedSingle() {
    doTestTodoUpdate("        int i;  // TODO ? todo f",
                     "        int i2;  // TODO ? todo f",
                     Collections.emptyList(),
                     Collections.singletonList("TODO ? todo f"));
  }

  public void testAddedSingle() {
    doTestTodoUpdate("    private void m() {",
                     "    private void m() { // todo test me",
                     Collections.singletonList("todo test me"),
                     Collections.emptyList());
  }

  public void testInEditedSingleWithDifferentOffsets() {
    doTestTodoUpdate(
      new String[]{"package com.andthere;",
        "        int i;  // TODO ? todo f"},
      new String[]{"package com.andthere;\n                                                                                           \n",
        "        int i2;  // TODO ? todo f"},
      Collections.emptyList(),
      Collections.singletonList("TODO ? todo f")
    );
  }

  public void testAddedSingleWithSimilarText() {
    doTestTodoUpdate("int i = 0; // todo check",
                     "int i = 0;// TODO ? todo f",
                     Collections.singletonList("TODO ? todo f"),
                     Collections.emptyList());
  }

  public void testNotChangedInTheMiddle() {
    doTestTodoUpdate(
      new String[]{"    /* 12345\n", "    todo in the middle\n", "    abcde\n"},
      new String[]{"    /* 123456\n", " 1  todo in the middle\n", "    abcde todo more\n"},
      Collections.singletonList("todo more"),
      Collections.singletonList("todo in the middle")
    );
  }

  public void testNotAffectedBetweenModifications() {
    doTestTodoUpdate(
      new String[]{"    /* 12345\n", "    abcde\n"},
      new String[]{"    /* 123456\n", "    abcde more\n"},
      Collections.emptyList(),
      Collections.emptyList()
    );
  }

  public void testSupportsCRLF() {
    myChildData = createChildData(myRootFile, "Test.java");
    Change change = change("        int i;  // TODO ? todo f", "        int i2;  // TODO ? todo f");

    ContentRevision crlfRevision = new SimpleContentRevision(StringUtil.convertLineSeparators(ourOldText, "\r\n"),
                                                             change.getBeforeRevision().getFile(), "1");
    change = new Change(crlfRevision, change.getAfterRevision());

    final TodoCheckinHandlerWorker worker = runTodoWorker(change);
    assertTrue(worker.getSkipped().isEmpty());
    Set<TodoItem> addedOrEditedTodos = worker.getAddedOrEditedTodos();
    Set<TodoItem> inChangedTodos = worker.getInChangedTodos();

    assertEquals(0, addedOrEditedTodos.size());
    assertEquals(1, inChangedTodos.size());
    final TextRange textRange = inChangedTodos.iterator().next().getTextRange();
    assertEquals("TODO ? todo f", textRange.substring(myNewText));
  }

  public void testMultiLineTodoCreation() {
    doTestTodoUpdate(
      "// TODO first line\n" +
      "// second line",
      "// second",
      "//  second",
      Collections.singletonList("TODO first line\nsecond line"),
      Collections.emptyList()
    );
  }

  public void testMultiLineTodoRemoval() {
    doTestTodoUpdate(
      "// TODO first line\n" +
      "//  second line",
      "//  second",
      "// second",
      Collections.singletonList("TODO first line"),
      Collections.emptyList()
    );
  }

  private void doTestTodoUpdate(String originalText, String changeWhat, String changeInto,
                                List<String> addedEditedTodosExpected, List<String> inChangedTodosExpected) {
    doTestTodoUpdate(originalText, new String[]{changeWhat}, new String[]{changeInto}, addedEditedTodosExpected, inChangedTodosExpected);
  }

  private void doTestTodoUpdate(String changeWhat, String changeInto,
                                List<String> addedEditedTodosExpected, List<String> inChangedTodosExpected) {
    doTestTodoUpdate(ourOldText, new String[]{changeWhat}, new String[]{changeInto}, addedEditedTodosExpected, inChangedTodosExpected);
  }

  private void doTestTodoUpdate(String[] changeWhat, String[] changeInto,
                                List<String> addedEditedTodosExpected, List<String> inChangedTodosExpected) {
    doTestTodoUpdate(ourOldText, changeWhat, changeInto, addedEditedTodosExpected, inChangedTodosExpected);
  }

  private void doTestTodoUpdate(String originalText, String[] changeWhat, String[] changeInto,
                                List<String> addedEditedTodosExpected, List<String> inChangedTodosExpected) {
    myChildData = createChildData(myRootFile, "Test.java");
    final Change change = change(originalText, changeWhat, changeInto);
    final TodoCheckinHandlerWorker worker = runTodoWorker(change);
    assertTrue(worker.getSkipped().isEmpty());
    Set<String> addedEditedTodosActual = worker.getAddedOrEditedTodos().stream().map(this::todoAsString).collect(Collectors.toSet());
    Set<String> inChangedTodosActual = worker.getInChangedTodos().stream().map(this::todoAsString).collect(Collectors.toSet());

    assertEquals(new HashSet<>(addedEditedTodosExpected), addedEditedTodosActual);
    assertEquals(new HashSet<>(inChangedTodosExpected), inChangedTodosActual);
  }

  private String todoAsString(TodoItem todoItem) {
    StringJoiner joiner = new StringJoiner("\n");
    JBIterable.of(todoItem.getTextRange()).append(todoItem.getAdditionalTextRanges()).forEach(range ->
                                                                                                joiner.add(range.substring(myNewText)));
    return joiner.toString();
  }

  @NotNull
  private TodoCheckinHandlerWorker runTodoWorker(Change change) {
    final TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, Collections.singletonList(change), null);
    worker.execute();
    return worker;
  }

  private Change change(final String what, final String into) {
    return change(ourOldText, new String[]{what}, new String[]{into});
  }

  private Change change(String originalText, final String[] what, final String[] into) {
    assert what.length == into.length;
    final StringBuilder sb = new StringBuilder(originalText);
    for (int i = 0; i < what.length; i++) {
      final String whatItem = what[i];
      final String intoItem = into[i];
      final int idx = sb.indexOf(whatItem);
      assert idx >= 0;
      sb.replace(idx, idx + whatItem.length(), intoItem);
    }
    myNewText = sb.toString();
    setFileText(myChildData, myNewText);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    final FilePath fp = VcsUtil.getFilePath(myChildData);
    return new Change(new SimpleContentRevision(originalText, fp, "1"), new SimpleContentRevision(myNewText, fp, "2"));
  }

  private final static String ourOldText = """
    package com.andthere;

    public class Essential {
     private final int i = 0; // todo check
     private final String myName;
       \s
        /* 12345
        todo in the middle
        abcde
         */
       \s
        private void m() {
            int i;  // TODO ? todo f
        }
       \s
        private void method() {
            for (Integer integer : new int[]{1, 2, 3}) {
                System.out.println(integer);
            }
        }
       \s
        some\s
                red todo
        lines
        // this ok todo ok
    }""";
}
