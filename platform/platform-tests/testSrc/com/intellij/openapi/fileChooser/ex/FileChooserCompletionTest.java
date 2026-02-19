// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.execution.wsl.WslRule;
import com.intellij.openapi.fileChooser.ex.FileTextFieldUtil.CompletionResult;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.TestFixtureRule;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ArrayUtil;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.assertj.core.api.Assertions.assertThat;

public class FileChooserCompletionTest {
  private static final TestFixtureRule appRule = new TestFixtureRule();
  private static final WslRule wslRule = new WslRule(false);
  @ClassRule public static final RuleChain ruleChain = RuleChain.outerRule(appRule).around(wslRule);

  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testEmpty() {
    if (!SystemInfo.isWindows) {
      assertComplete("", ArrayUtil.EMPTY_STRING_ARRAY, Map.of(), "", null);
    }

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
  public void testWindowsRoots() {
    assumeWindows();

    String[] fsRoots = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false).map(Path::toString).toArray(String[]::new);
    String[] wslRoots = wslRule.getVms().stream().map(d -> StringUtil.trimTrailing(d.getUNCRootPath().toString(), '\\')).toArray(String[]::new);
    String[] allRoots = ArrayUtil.mergeArrays(fsRoots, wslRoots, String[]::new);
    String firstRoot = Stream.of(allRoots).sorted().findFirst().orElseThrow();
    assertComplete("", allRoots, Map.of(), "", firstRoot);

    String sysDrive = System.getenv("SystemDrive");
    assertThat(sysDrive).isNotBlank();
    String[] expected = {sysDrive + '\\'};
    assertComplete(sysDrive.substring(0, 1), expected, Map.of(), "", expected[0]);
    assertComplete(sysDrive, expected, Map.of(), "", expected[0]);

    if (wslRoots.length != 0) {
      String firstWsl = Stream.of(wslRoots).sorted().findFirst().orElseThrow();
      int p = firstWsl.indexOf('\\', 2);
      assertThat(p).withFailMessage("Malformed name: '%s'", firstWsl).isGreaterThan(2);

      assertComplete("\\\\", wslRoots, Map.of(), "", firstWsl);
      assertComplete(firstWsl.substring(0, 3), wslRoots, Map.of(), "", firstWsl);  // '\\w'
      assertComplete(firstWsl.substring(0, p + 1), wslRoots, Map.of(), "", firstWsl);  // '\\wsl$\'
      assertComplete(firstWsl, new String[]{firstWsl}, Map.of(), "", firstWsl);  // '\\wsl$\xxx'
      assertComplete(firstWsl + "\\ho", new String[]{"home"}, Map.of(), "", "home");  // '\\wsl$\xxx\ho'
    }
  }

  private void assertComplete(String typed, String[] expected, String preselected) {
    assertComplete(typed, expected, Map.of(), tempDir.getRoot().getPath(), preselected);
  }

  private static void assertComplete(String typed, String[] expected, Map<String, String> macros, String completionBase, String preselected) {
    LocalFsFinder finder = new LocalFsFinder(false).withBaseDir(null);
    String input = completionBase + typed.replace("/", finder.getSeparator());
    CompletionResult result = FileTextFieldUtil.processCompletion(input, finder, file -> true, new TreeMap<>(macros), null);

    String[] actualVariants = result.variants.stream().map(f -> FileTextFieldUtil.getLookupString(f, finder, result)).toArray(String[]::new);
    String[] expectedVariants = Stream.of(expected).map(s -> s.replace("/", finder.getSeparator())).toArray(String[]::new);
    assertThat(actualVariants).containsExactlyInAnyOrder(expectedVariants);

    String actualSelected = result.preselected != null ? FileTextFieldUtil.getLookupString(result.preselected, finder, result) : null;
    String expectedSelected = preselected != null ? preselected.replace("/", finder.getSeparator()) : null;
    assertThat(actualSelected).isEqualTo(expectedSelected);
    if (preselected != null) {
      assertThat(result.variants).contains(result.preselected);
    }
  }
}
