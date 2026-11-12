// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.editor.GroovyLiteralCopyPasteProcessor;
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.DOLLAR_SLASHY;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.DOUBLE_QUOTED;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.SINGLE_QUOTED;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.SLASHY;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.TRIPLE_DOUBLE_QUOTED;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.TRIPLE_SINGLE_QUOTED;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class GroovyCopyPasteStringTest implements BaseTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void copy() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, String> map = new LinkedHashMap<>(34);
      map.put("<selection>'\\\\'</selection>", "'\\\\'");
      map.put("<selection>'\\\\</selection>'", "'\\\\");
      map.put("'<selection>\\\\'</selection>", "\\\\'");
      map.put("'<selection>\\\\</selection>'", "\\");

      map.put("<selection>'''\\\\'''</selection>", "'''\\\\'''");
      map.put("'<selection>''\\\\'''</selection>", "''\\\\'''");
      map.put("''<selection>'\\\\'''</selection>", "'\\\\'''");
      map.put("'''<selection>\\\\'''</selection>", "\\\\'''");
      map.put("<selection>'''\\\\</selection>'''", "'''\\\\");
      map.put("<selection>'''\\\\'</selection>''", "'''\\\\'");
      map.put("<selection>'''\\\\''</selection>'", "'''\\\\''");
      map.put("'''<selection>\\\\</selection>'''", "\\");

      map.put("<selection>\"\\\\\"</selection>", "\"\\\\\"");
      map.put("<selection>\"\\\\</selection>\"", "\"\\\\");
      map.put("\"<selection>\\\\\"</selection>", "\\\\\"");
      map.put("\"<selection>\\\\</selection>\"", "\\");

      map.put("<selection>\"\"\"\\\\\"\"\"</selection>", "\"\"\"\\\\\"\"\"");
      map.put("\"<selection>\"\"\\\\\"\"\"</selection>", "\"\"\\\\\"\"\"");
      map.put("\"\"<selection>\"\\\\\"\"\"</selection>", "\"\\\\\"\"\"");
      map.put("\"\"\"<selection>\\\\\"\"\"</selection>", "\\\\\"\"\"");
      map.put("<selection>\"\"\"\\\\</selection>\"\"\"", "\"\"\"\\\\");
      map.put("<selection>\"\"\"\\\\\"</selection>\"\"", "\"\"\"\\\\\"");
      map.put("<selection>\"\"\"\\\\\"\"</selection>\"", "\"\"\"\\\\\"\"");
      map.put("\"\"\"<selection>\\\\</selection>\"\"\"", "\\");

      map.put("<selection>/\\//</selection>", "/\\//");
      map.put("<selection>/\\/</selection>/", "/\\/");
      map.put("/<selection>\\//</selection>", "\\//");
      map.put("/<selection>\\/</selection>/", "/");

      map.put("<selection>$/\\u2318/$</selection>", "$/\\u2318/$");
      map.put("$<selection>/\\u2318/$</selection>", "/\\u2318/$");
      map.put("$/<selection>\\u2318/$</selection>", "\\u2318/$");
      map.put("<selection>$/\\u2318</selection>/$", "$/\\u2318");
      map.put("<selection>$/\\u2318/</selection>$", "$/\\u2318/");
      map.put("$/<selection>\\u2318</selection>/$", "⌘");

      map.forEach((text, expectedCopy) -> {
        doCopyTest(text, expectedCopy);
      });
    });
  }

  @Test
  public void find_string_kind_for_paste() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, StringKind> map = new LinkedHashMap<>(84);
      // empty strings
      map.put("'<caret>'", SINGLE_QUOTED);
      map.put("'''<caret>'''", TRIPLE_SINGLE_QUOTED);
      map.put("\"<caret>\"", DOUBLE_QUOTED);
      map.put("\"\"\"<caret>\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("/<caret>/", null);
      map.put("$/<caret>/$", DOLLAR_SLASHY);

      // in template strings without injections
      map.put("\"<caret>hi there\"", DOUBLE_QUOTED);
      map.put("\"hi<caret> there\"", DOUBLE_QUOTED);
      map.put("\"hi there<caret>\"", DOUBLE_QUOTED);
      map.put("\"\"\"<caret>hi there\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("\"\"\"hi<caret> there\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("\"\"\"hi there<caret>\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("/<caret>hi there/", SLASHY);
      map.put("/hi<caret> there/", SLASHY);
      map.put("/hi there<caret>/", SLASHY);
      map.put("$/<caret>hi there/$", DOLLAR_SLASHY);
      map.put("$/hi<caret> there/$", DOLLAR_SLASHY);
      map.put("$/hi there<caret>/$", DOLLAR_SLASHY);

      // in template strings with injections
      map.put("\"<caret>hi there${}\"", DOUBLE_QUOTED);
      map.put("\"hi<caret> there${}\"", DOUBLE_QUOTED);
      map.put("\"hi there<caret>${}\"", DOUBLE_QUOTED);
      map.put("\"${}<caret>hi there\"", DOUBLE_QUOTED);
      map.put("\"${}hi<caret> there\"", DOUBLE_QUOTED);
      map.put("\"${}hi there<caret>\"", DOUBLE_QUOTED);
      map.put("\"\"\"<caret>hi there${}\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("\"\"\"hi<caret> there${}\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("\"\"\"hi there<caret>${}\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("\"\"\"${}<caret>hi there\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("\"\"\"${}hi<caret> there\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("\"\"\"${}hi there<caret>\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("/<caret>hi there${}/", SLASHY);
      map.put("/hi<caret> there${}/", SLASHY);
      map.put("/hi there<caret>${}/", SLASHY);
      map.put("/${}<caret>hi there/", SLASHY);
      map.put("/${}hi<caret> there/", SLASHY);
      map.put("/${}hi there<caret>/", SLASHY);
      map.put("$/<caret>hi there${}/$", DOLLAR_SLASHY);
      map.put("$/hi<caret> there${}/$", DOLLAR_SLASHY);
      map.put("$/hi there<caret>${}/$", DOLLAR_SLASHY);
      map.put("$/${}<caret>hi there/$", DOLLAR_SLASHY);
      map.put("$/${}hi<caret> there/$", DOLLAR_SLASHY);
      map.put("$/${}hi there<caret>/$", DOLLAR_SLASHY);

      // between injection and opening quote
      map.put("\"<caret>${}hi there\"", DOUBLE_QUOTED);
      map.put("\"\"\"<caret>${}hi there\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("/<caret>${}hi there/", SLASHY);
      map.put("$/<caret>${}hi there/$", DOLLAR_SLASHY);

      // between injections
      map.put("\"hi ${}<caret>${} there\"", DOUBLE_QUOTED);
      map.put("\"\"\"hi ${}<caret>${} there\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("/hi ${}<caret>${} there/", SLASHY);
      map.put("$/hi ${}<caret>${} there/$", DOLLAR_SLASHY);

      // between injection and closing quote
      map.put("\"hi there${}<caret>\"", DOUBLE_QUOTED);
      map.put("\"\"\"hi there${}<caret>\"\"\"", TRIPLE_DOUBLE_QUOTED);
      map.put("/hi there${}<caret>/", SLASHY);
      map.put("$/hi there${}<caret>/$", DOLLAR_SLASHY);

      // inside injection
      map.put("\"hi $<caret>{} there\"", null);
      map.put("\"hi ${<caret>} there\"", null);
      map.put("\"\"\"hi $<caret>{} there\"\"\"", null);
      map.put("\"\"\"hi ${<caret>} there\"\"\"", null);
      map.put("/hi $<caret>{} there/", null);
      map.put("/hi ${<caret>} there/", null);
      map.put("$/hi $<caret>{} there/$", null);
      map.put("$/hi ${<caret>} there/$", null);

      // outside of quotes
      map.put("<caret>'hi'", null);
      map.put("'hi'<caret>", null);
      map.put("<caret>'''hi'''", null);
      map.put("'<caret>''hi'''", null);
      map.put("''<caret>'hi'''", null);
      map.put("'''hi'''<caret>", null);
      map.put("'''hi''<caret>'", null);
      map.put("'''hi'<caret>''", null);
      map.put("<caret>\"hi\"", null);
      map.put("\"hi\"<caret>", null);
      map.put("<caret>\"\"\"hi\"\"\"", null);
      map.put("\"<caret>\"\"hi\"\"\"", null);
      map.put("\"\"<caret>\"hi\"\"\"", null);
      map.put("\"\"\"hi\"\"\"<caret>", null);
      map.put("\"\"\"hi\"\"<caret>\"", null);
      map.put("\"\"\"hi\"<caret>\"\"", null);
      map.put("<caret>/hi/", null);
      map.put("/hi/<caret>", null);
      map.put("<caret>$/hi/$", null);
      map.put("$<caret>/hi/$", null);
      map.put("$/hi/$<caret>", null);
      map.put("$/hi/<caret>$", null);

      map.forEach((text, expectedKind) -> {
          PsiFile file = getFixture().configureByText("_.groovy", text);
          SelectionModel selectionModel = getFixture().getEditor().getSelectionModel();
          int selectionStart = selectionModel.getSelectionStart();
          int selectionEnd = selectionModel.getSelectionEnd();
          StringKind kind = GroovyLiteralCopyPasteProcessor.findStringKind(file, selectionStart, selectionEnd);
          Assertions.assertEquals(expectedKind, kind, text);
      });
    });
  }

  @Test
  public void paste_raw() {
    runInEdtAndWait(() -> {
      List<List<String>> data = Arrays.asList(
        new ArrayList<>(Arrays.asList("'<selection>\\$ \\' \\\" \\\n \\u2318</selection>'", "\"<caret>\"", "\"\\$ \\' \\\" \\\n \\u2318\"")),
        new ArrayList<>(Arrays.asList("\"<selection>\\$ \\' \\\" \\\n \\u2318</selection>\"", "'<caret>'", "'\\$ \\' \\\" \\\n \\u2318'")),
        new ArrayList<>(Arrays.asList("\"<selection>\\${foo}</selection>\"", "\"<caret>\"", "\"\\${foo}\"")),
        new ArrayList<>(Arrays.asList("\"<selection>\\$bar</selection>\"", "\"<caret>\"", "\"\\$bar\""))
      );

      TestUtils.runAll(data, entry -> {
          doCopyPasteTest(entry.get(0), entry.get(1), entry.get(2));
      });
    });
  }

  @Test
  public void paste_new_line() {
    runInEdtAndWait(() -> {
      final String from = "<selection>\n</selection>";
      LinkedHashMap<String, String> map = new LinkedHashMap<>(6);
      map.put("'<caret>'", "'\\n'");
      map.put("'''<caret>'''", "'''\n'''");
      map.put("\"<caret>\"", "\"\\n\"");
      map.put("\"\"\"<caret>\"\"\"", "\"\"\"\n\"\"\"");
      map.put("/ <caret>/", "/ \n/");
      map.put("$/<caret>/$", "$/\n/$");

      map.forEach((text, expectedCopy) -> {
        doCopyPasteTest(from, text, expectedCopy);
      });
    });
  }

  @Test
  public void multiline_paste() {
    runInEdtAndWait(() -> {
      final String from = "<selection>hi\nthere\n</selection>";
      LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(6);
      map.put("'<caret>'", "'hi\\n' +\n        'there\\n'");
      map.put("'''<caret>'''", "'''hi\nthere\n'''");
      map.put("\"<caret>\"", "\"hi\\n\" +\n        \"there\\n\"");
      map.put("\"\"\"<caret>\"\"\"", "\"\"\"hi\nthere\n\"\"\"");
      map.put("/ <caret>/", "/ hi\nthere\n/");
      map.put("$/<caret>/$", "$/hi\nthere\n/$");

      map.forEach((text, expectedCopy) -> {
        doCopyPasteTest(from, text, expectedCopy);
      });
    });
  }

  @Test
  public void multiline_paste_raw() {
    runInEdtAndWait(() -> {
      final String from = "$/<selection>hi\nthere\\u2318</selection>/$";
      LinkedHashMap<String, String> map = new LinkedHashMap<>(6);
      map.put("'<caret>'", "'hi\\n' +\n        'there⌘'");
      map.put("'''<caret>'''", "'''hi\nthere\\u2318'''");
      map.put("\"<caret>\"", "\"hi\\n\" +\n        \"there⌘\"");
      map.put("\"\"\"<caret>\"\"\"", "\"\"\"hi\nthere\\u2318\"\"\"");
      map.put("/ <caret>/", "/ hi\nthere\\u2318/");
      map.put("$/<caret>/$", "$/hi\nthere\\u2318/$");


      map.forEach((text, expectedCopy) -> {
        doCopyPasteTest(from, text, expectedCopy);
      });
    });
  }

  @Test
  public void paste_injection() {
    runInEdtAndWait(() -> {
      List<List<String>> data = Arrays.asList(new ArrayList<>(Arrays.asList("<selection>$a</selection>", "\"<caret>\"", "\"$a\"")),
        new ArrayList<>(Arrays.asList("\"<selection>$a</selection>\"", "\"<caret>\"", "\"$a\"")),
        new ArrayList<>(Arrays.asList("<selection>${a}</selection>", "\"<caret>\"", "\"${a}\"")),
        new ArrayList<>(Arrays.asList("\"<selection>${a}</selection>\"", "\"<caret>\"", "\"${a}\""))
      );

      TestUtils.runAll(data, entry -> {
          doCopyPasteTest(entry.get(0), entry.get(1), entry.get(2));
      });
    });
  }

  private void doCopyTest(String text, String expectedCopy) {
    getFixture().configureByText("from.groovy", text);
    getFixture().performEditorAction(IdeActions.ACTION_COPY);
    getFixture().configureByText("to.txt", "");
    getFixture().performEditorAction(IdeActions.ACTION_PASTE);
    getFixture().checkResult(expectedCopy);
  }

  private void doCopyPasteTest(String fromText, String toText, String expected) {
    getFixture().configureByText("from.groovy", fromText);
    getFixture().performEditorAction(IdeActions.ACTION_COPY);
    getFixture().configureByText("to.groovy", toText);
    getFixture().performEditorAction(IdeActions.ACTION_PASTE);
    getFixture().checkResult(expected);
  }
}
