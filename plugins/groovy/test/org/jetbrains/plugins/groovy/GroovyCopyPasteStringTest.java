// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.editor.GroovyLiteralCopyPasteProcessor;
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind;
import org.jetbrains.plugins.groovy.util.BaseTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.*;

public class GroovyCopyPasteStringTest extends GroovyLatestTest implements BaseTest {
  @Test
  public void copy() {
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
  }

  @Test
  public void find_string_kind_for_paste() {
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
        Assert.assertEquals(text, expectedKind, kind);
    });
  }

  @Test
  public void paste_raw() {
    List<List<String>> data = Arrays.asList(
      new ArrayList<>(Arrays.asList("'<selection>\\$ \\' \\\" \\\n \\u2318</selection>'", "\"<caret>\"", "\"\\$ \\' \\\" \\\n \\u2318\"")),
      new ArrayList<>(Arrays.asList("\"<selection>\\$ \\' \\\" \\\n \\u2318</selection>\"", "'<caret>'", "'\\$ \\' \\\" \\\n \\u2318'")),
      new ArrayList<>(Arrays.asList("\"<selection>\\${foo}</selection>\"", "\"<caret>\"", "\"\\${foo}\"")),
      new ArrayList<>(Arrays.asList("\"<selection>\\$bar</selection>\"", "\"<caret>\"", "\"\\$bar\""))
    );

    TestUtils.runAll(data, entry -> {
        doCopyPasteTest(entry.get(0), entry.get(1), entry.get(2));
    });
  }

  @Test
  public void paste_new_line() {
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
  }

  @Test
  public void multiline_paste() {
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
  }

  @Test
  public void multiline_paste_raw() {
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
  }

  @Test
  public void paste_injection() {
    List<List<String>> data = Arrays.asList(new ArrayList<>(Arrays.asList("<selection>$a</selection>", "\"<caret>\"", "\"$a\"")),
      new ArrayList<>(Arrays.asList("\"<selection>$a</selection>\"", "\"<caret>\"", "\"$a\"")),
      new ArrayList<>(Arrays.asList("<selection>${a}</selection>", "\"<caret>\"", "\"${a}\"")),
      new ArrayList<>(Arrays.asList("\"<selection>${a}</selection>\"", "\"<caret>\"", "\"${a}\""))
    );

    TestUtils.runAll(data, entry -> {
        doCopyPasteTest(entry.get(0), entry.get(1), entry.get(2));
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
