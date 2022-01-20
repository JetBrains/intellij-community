// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ArrayUtil;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileChooserCompletionTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testEmpty() {
    assertComplete("", ArrayUtil.EMPTY_STRING_ARRAY, Map.of(), "", null);
    assertComplete("1", ArrayUtil.EMPTY_STRING_ARRAY, Map.of(), "", null);
  }

  @Test
  public void testStartMatching() {
    tempDir.newDirectory("folder1/folder11/folder21");
    tempDir.newDirectory("folder1/folder12");
    tempDir.newFile("a");
    tempDir.newFile("file1");
    Map<String, String> macros = Map.of();
    String completionBase = tempDir.getRoot().getPath();

    assertComplete("f", ArrayUtil.EMPTY_STRING_ARRAY, macros, completionBase, null);

    assertComplete("/", new String[]{"a", "folder1", "file1"}, macros, completionBase, "a");
    assertComplete("/f", new String[]{"folder1", "file1"}, macros, completionBase, "file1");
    assertComplete("/fo", new String[]{"folder1"}, macros, completionBase, "folder1");
    assertComplete("/folder", new String[]{"folder1"}, macros, completionBase, "folder1");
    assertComplete("/folder1", new String[]{"folder1"}, macros, completionBase, "folder1");

    assertComplete("/folder1/", new String[]{"folder11", "folder12"}, macros, completionBase, "folder11");
    assertComplete("/folder1/folder1", new String[]{"folder11", "folder12"}, macros, completionBase, "folder11");

    assertComplete("/foo", ArrayUtil.EMPTY_STRING_ARRAY, macros, completionBase, null);

    tempDir.newFile("qw/child.txt");
    tempDir.newDirectory("qwe");
    assertComplete("/qw", new String[]{"qw", "qwe"}, macros, completionBase, "qw");
    assertComplete("/qw/", new String[]{"child.txt"}, macros, completionBase, "child.txt");
  }

  @Test
  public void testMiddleMatching() {
    tempDir.newDirectory("folder1");

    assertComplete("/old", new String[]{"folder1"}, Map.of(), tempDir.getRoot().getPath(), "folder1");
  }

  @Test
  public void testMacros() {
    File dir21 = tempDir.newDirectory("dir1/dir11/dir21");
    Map<String, String> macros = Map.of(
      "$DIR_11$", dir21.getParent(),
      "$DIR_21$", dir21.getPath(),
      "$WHATEVER$", "/some_path");

    assertComplete("$", new String[]{"$DIR_11$", "$DIR_21$"}, macros, "", "$DIR_11$");
  }

  private void assertComplete(String typed, String[] expected, Map<String, String> macros, String completionBase, String preselected) {
    LocalFsFinder finder = new LocalFsFinder(false).withBaseDir(null);
    FileTextFieldImpl.CompletionResult result = new FileTextFieldImpl.CompletionResult();
    result.myCompletionBase = completionBase + typed.replace("/", finder.getSeparator());
    new FileTextFieldImpl(new JTextField(), finder, file -> true, macros, getTestRootDisposable()).processCompletion(result);

    String[] actualVariants = result.myToComplete.stream().map(f -> toFileText(f, result, finder.getSeparator())).toArray(String[]::new);
    String[] expectedVariants = Stream.of(expected).map(s -> s.replace("/", finder.getSeparator())).toArray(String[]::new);
    assertThat(actualVariants).containsExactlyInAnyOrder(expectedVariants);

    String preselectedText = preselected != null ? preselected.replace("/", finder.getSeparator()) : null;
    assertEquals(preselectedText, toFileText(result.myPreselected, result, finder.getSeparator()));
    if (preselected != null) {
      assertTrue(result.myToComplete.contains(result.myPreselected));
    }
  }

  private static String toFileText(FileLookup.LookupFile file, FileTextFieldImpl.CompletionResult completion, String separator) {
    String text = file == null ? null : file.getMacro() != null ? file.getMacro() : file.getName();
    return text == null ? null : (completion.myKidsAfterSeparator.contains(file) ? separator : "") + text;
  }
}
