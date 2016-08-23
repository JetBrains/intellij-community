/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class ExpectedHighlightingDataTest {
  private static Map<String, ExpectedHighlightingData.ExpectedHighlightingSet> TYPES;

  @BeforeClass
  public static void setUp() {
    TYPES = new HashMap<>();
    TYPES.put("err", new ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.ERROR, false, true));
    TYPES.put("warn", new ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.WARNING, false, true));
    TYPES.put("eol_err", new ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.ERROR, true, true));
  }

  @AfterClass
  public static void tearDown() {
    TYPES.clear();
    TYPES = null;
  }

  @Test
  public void empty() {
    doTest("text", Collections.<HighlightInfo>emptyList(), "text");
  }

  @Test
  public void sequential() {
    doTest("_my text_",
           asList(error(1, 3, "1"), error(4, 8, "2")),
           "_<err descr=\"1\">my</err> <err descr=\"2\">text</err>_");
  }

  @Test
  public void simpleNested() {
    doTest("[(nested)]",
           asList(error(1, 9, "1"), error(2, 8, "2")),
           "[<err descr=\"1\">(<err descr=\"2\">nested</err>)</err>]");
  }

  @Test
  public void deepNested() {
    doTest("m1(m2(m3(m4(x))))",
           asList(error(3, 16, "m1"), error(6, 15, "m2"), error(9, 14, "m3"), error(12, 13, "m4")),
           "m1(<err descr=\"m1\">m2(<err descr=\"m2\">m3(<err descr=\"m3\">m4(<err descr=\"m4\">x</err>)</err>)</err>)</err>)");
  }

  @Test
  public void sameStart() {
    doTest("same start",
           asList(error(0, 4, "1"), error(0, 10, "2")),
           "<err descr=\"2\"><err descr=\"1\">same</err> start</err>");
  }

  @Test
  public void sameEnd() {
    doTest("same end",
           asList(error(0, 8, "1"), error(5, 8, "2")),
           "<err descr=\"1\">same <err descr=\"2\">end</err></err>");
  }

  @Test
  public void sameBothBounds() {
    doTest("same",
           asList(error(0, 4, "-"), warning(0, 4, "-")),
           "<err descr=\"-\"><warn descr=\"-\">same</warn></err>");
  }

  @Test
  public void samePriority() {
    doTest("same",
           asList(warning(0, 4, "1"), warning(0, 4, "2")),
           "<warn descr=\"1\"><warn descr=\"2\">same</warn></warn>");
  }

  @Test
  public void twoNests() {
    doTest("(two nests)",
           asList(error(0, 11, "-"), error(1, 4, "1"), error(5, 10, "2")),
           "<err descr=\"-\">(<err descr=\"1\">two</err> <err descr=\"2\">nests</err>)</err>");
  }

  @Test
  public void realistic() {
    doTest("one and (two nests)",
           asList(error(4, 7, "-"), error(8, 19, "-"), error(9, 12, "1"), error(13, 18, "2")),
           "one <err descr=\"-\">and</err> <err descr=\"-\">(<err descr=\"1\">two</err> <err descr=\"2\">nests</err>)</err>");
  }

  @Test
  public void twoEOLs() {
    doTest("text\nmore text",
           asList(eolError(4, 4, "1"), eolError(4, 4, "2")),
           "text<eol_err descr=\"2\"></eol_err><eol_err descr=\"1\"></eol_err>\nmore text");
  }

  @Test
  public void eolAfterError() {
    doTest("some error\nmore text",
           asList(error(5, 10, "1"), eolError(10, 10, "2")),
           "some <err descr=\"1\">error</err><eol_err descr=\"2\"></eol_err>\nmore text");
  }

  @Test
  public void consecutiveNests() {
    doTest(" ab ",
           asList(error(1, 2, "a1"), error(1, 2, "a2"), error(2, 3, "b1"), error(2, 3, "b2")),
           " <err descr=\"a1\"><err descr=\"a2\">a</err></err><err descr=\"b1\"><err descr=\"b2\">b</err></err> ");
  }

  private static void doTest(String original, Collection<HighlightInfo> highlighting, String expected) {
    String text = ExpectedHighlightingData.composeText(TYPES, highlighting, original);
    assertEquals(expected, text);
  }

  private static HighlightInfo error(int start, int end, @NotNull String description) {
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR);
    builder.range(start, end);
    builder.descriptionAndTooltip(description);
    return builder.create();
  }
  private static HighlightInfo warning(int start, int end, @NotNull String description) {
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING);
    builder.range(start, end);
    builder.descriptionAndTooltip(description);
    return builder.create();
  }

  private static HighlightInfo eolError(int start, int end, @NotNull String description) {
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR);
    builder.range(start, end);
    builder.description(description);
    builder.endOfLine();
    return builder.create();
  }
}
