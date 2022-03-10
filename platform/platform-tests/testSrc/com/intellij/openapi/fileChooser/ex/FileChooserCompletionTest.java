// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ArrayUtil;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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

    assertComplete("f", ArrayUtil.EMPTY_STRING_ARRAY, null);

    assertComplete("/", new String[]{"a", "folder1", "file1"}, "a");
    assertComplete("/f", new String[]{"folder1", "file1"}, "file1");
    assertComplete("/fo", new String[]{"folder1"}, "folder1");
    assertComplete("/folder", new String[]{"folder1"}, "folder1");
    assertComplete("/folder1", new String[]{"folder1"}, "folder1");

    assertComplete("/folder1/", new String[]{"folder11", "folder12"}, "folder11");
    assertComplete("/folder1/folder1", new String[]{"folder11", "folder12"}, "folder11");

    assertComplete("/foo", ArrayUtil.EMPTY_STRING_ARRAY, null);

    tempDir.newFile("qw/child.txt");
    tempDir.newDirectory("qwe");
    assertComplete("/qw", new String[]{"qw", "qwe"}, "qw");
    assertComplete("/qw/", new String[]{"child.txt"}, "child.txt");
  }

  @Test
  public void testMiddleMatching() {
    tempDir.newDirectory("folder1");

    assertComplete("/old", new String[]{"folder1"}, "folder1");
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

  @Test
  public void testWslMatching() throws Exception {
    assumeTrue("No WSL", WSLUtil.isSystemCompatible());
    List<WSLDistribution> vms = WslDistributionManager.getInstance().getInstalledDistributionsFuture().get(30, TimeUnit.SECONDS);
    assumeTrue("No WSL distros", !vms.isEmpty());

    String[] roots = vms.stream().map(d -> StringUtil.trimTrailing(d.getUNCRootPath().toString(), '\\')).toArray(String[]::new);
    String first = Stream.of(roots).sorted().findFirst().orElseThrow();
    int p = first.indexOf('\\', 2);
    assertThat(p).withFailMessage("Malformed name: '%s'", first).isGreaterThan(2);

    assertComplete("\\\\", roots, Map.of(), "", first);
    assertComplete(first.substring(0, 3), roots, Map.of(), "", first);  // '\\w'
    assertComplete(first.substring(0, p + 1), roots, Map.of(), "", first);  // '\\wsl$\'
    assertComplete(first, new String[]{first}, Map.of(), "", first);  // '\\wsl$\xxx'
    assertComplete(first + "\\ho", new String[]{"home"}, Map.of(), "", "home");  // '\\wsl$\xxx\ho'
  }

  private void assertComplete(String typed, String[] expected, String preselected) {
    assertComplete(typed, expected, Map.of(), tempDir.getRoot().getPath(), preselected);
  }

  private static void assertComplete(String typed, String[] expected, Map<String, String> macros, String completionBase, String preselected) {
    LocalFsFinder finder = new LocalFsFinder(false).withBaseDir(null);
    FileTextFieldImpl.CompletionResult result = new FileTextFieldImpl.CompletionResult();
    result.myCompletionBase = completionBase + typed.replace("/", finder.getSeparator());
    FileTextFieldUtil.processCompletion(result, finder, file -> true, new TreeMap<>(macros));

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
