/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.HunkStatus;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;

import java.util.*;

public class GenericApplyPatchTest extends PlatformTestCase {
  public void testSeveralSteps() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "6"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "7"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "8"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "9"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "10"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\n2\n3\n4\n7\n8\n11\naaa", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\n2\n5\n6\n9\n10\n11\naaa", after);
  }

  public void testExchangedParts() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "2a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "3a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "6"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "7"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "6a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "7a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "8"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "9"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "10"));

    final GenericPatchApplier gap = new GenericPatchApplier("5\n6\n7\n8\n1\n2\n3\n4\n9\nextra line", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("5\n6a\n7a\n8\n1\n2a\n3a\n4\n9\nextra line", after);
  }

  public void testDeleteAlmostOk() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n3\n4\n9\n8\n11\naaa", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n9\n8\n11\naaa", after);
  }

  public void testInsertAlmostOk() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n9\n8\n11\naaa", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n3\n4\n5\n9\n8\n11\naaa", after);
  }

  public void testInsertAlmostOkAlreadyApplied() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n3\n4\n9\n8\n11\naaa", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.ALREADY_APPLIED, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n3\n4\n5\n9\n8\n11\naaa", after);
  }

  public void testChangeAlmostOk() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "b"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n3\n4\n9\n8\n11\naaa", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\na\nb\n9\n8\n11\naaa", after);
  }

  public void testChangeAlmostOkAlreadyApplied() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "b"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\na\n9\n8\n11\naaa", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.ALREADY_APPLIED, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\na\nb\nc\n9\n8\n11\naaa", after);
  }

  public void testChangeAlmostOkManySteps() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-1a"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-2a"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-3a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-3b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-4a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-4b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n-1\n4\n9\n8\n11" +
                                                            "\naaa\n2\n-1\n-2\n-3", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n-1\n4\n9\n8\n11\naaa\n2\n-1a\n-2a\n-3a\n-3b\n-4a\n-4b\n", after);
  }

  public void testFirstNewLine() {
    final PatchHunk patchHunk = new PatchHunk(1, 2, 1, 3);
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, ""));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    final GenericPatchApplier gap = new GenericPatchApplier("1\n2\n", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("\n1\n2\n", after);
  }

    public void testNewEmptyLine() {
    final PatchHunk patchHunk = new PatchHunk(1, 3, 1, 4);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "0"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, ""));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\n1\n2\n", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\n\n1\n2\n", after);
  }

  public void testInsertionsIntoTransformationsCoinsidence() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append(String.valueOf(i + 1)).append('\n');
    }
    final GenericPatchApplier gap = new GenericPatchApplier(sb.toString(), Collections.emptyList());
    final List<String> strings = Arrays.asList("5", "6", "7");
    // coincidence
    gap.putCutIntoTransformations(new TextRange(4, 6), new GenericPatchApplier.MyAppliedData(strings, true, true, true, GenericPatchApplier.ChangeType.REPLACE));
    final TreeMap<TextRange,GenericPatchApplier.MyAppliedData> transformations = gap.getTransformations();
    Assert.assertTrue(transformations.isEmpty());
    Assert.assertEquals(ApplyPatchStatus.ALREADY_APPLIED, gap.getStatus());
  }

  public void testInsertionsIntoTransformationsInsert() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append(String.valueOf(i + 1)).append('\n');
    }
    final GenericPatchApplier gap = new GenericPatchApplier(sb.toString(), Collections.emptyList());
    final List<String> strings = Arrays.asList("5", "6", "ins", "7");
    // coincidence
    gap.putCutIntoTransformations(new TextRange(4, 6), new GenericPatchApplier.MyAppliedData(strings, true, true, true, GenericPatchApplier.ChangeType.REPLACE));
    final TreeMap<TextRange,GenericPatchApplier.MyAppliedData> transformations = gap.getTransformations();
    Assert.assertFalse(transformations.isEmpty());
    Assert.assertEquals(1, transformations.size());

    final Map.Entry<TextRange, GenericPatchApplier.MyAppliedData> entry = transformations.entrySet().iterator().next();
    final TextRange key = entry.getKey();
    Assert.assertTrue(key.getStartOffset() == 5 && key.getEndOffset() == 5);
    final List<String> list = entry.getValue().getList();
    Assert.assertTrue(list.size() == 2);
    Assert.assertTrue("6".equals(list.get(0)));
    Assert.assertTrue("ins".equals(list.get(1)));
  }

  public void testInsertionsIntoTransformationsInsert0() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append(String.valueOf(i + 1)).append('\n');
    }
    final GenericPatchApplier gap = new GenericPatchApplier(sb.toString(), Collections.emptyList());
    final List<String> strings = Arrays.asList("ins", "5", "6", "7");
    // coincidence
    gap.putCutIntoTransformations(new TextRange(4, 6), new GenericPatchApplier.MyAppliedData(strings, true, true, true, GenericPatchApplier.ChangeType.REPLACE));
    final TreeMap<TextRange,GenericPatchApplier.MyAppliedData> transformations = gap.getTransformations();
    Assert.assertFalse(transformations.isEmpty());
    Assert.assertEquals(1, transformations.size());

    final Map.Entry<TextRange, GenericPatchApplier.MyAppliedData> entry = transformations.entrySet().iterator().next();
    final TextRange key = entry.getKey();
    Assert.assertTrue(key.getStartOffset() == 4 && key.getEndOffset() == 4);
    final List<String> list = entry.getValue().getList();
    Assert.assertTrue(list.size() == 2);
    Assert.assertTrue("ins".equals(list.get(0)));
    Assert.assertTrue("5".equals(list.get(1)));
  }

  public void testInsertionsIntoTransformationsInsert1() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append(String.valueOf(i + 1)).append('\n');
    }
    final GenericPatchApplier gap = new GenericPatchApplier(sb.toString(), Collections.emptyList());
    final List<String> strings = Arrays.asList("5", "6", "7", "ins");
    // coincidence
    gap.putCutIntoTransformations(new TextRange(4, 6), new GenericPatchApplier.MyAppliedData(strings, true, true, true, GenericPatchApplier.ChangeType.REPLACE));
    final TreeMap<TextRange,GenericPatchApplier.MyAppliedData> transformations = gap.getTransformations();
    Assert.assertFalse(transformations.isEmpty());
    Assert.assertEquals(1, transformations.size());

    final Map.Entry<TextRange, GenericPatchApplier.MyAppliedData> entry = transformations.entrySet().iterator().next();
    final TextRange key = entry.getKey();
    Assert.assertTrue(key.getStartOffset() == 6 && key.getEndOffset() == 6);
    final List<String> list = entry.getValue().getList();
    Assert.assertTrue(list.size() == 2);
    Assert.assertTrue("7".equals(list.get(0)));
    Assert.assertTrue("ins".equals(list.get(1)));
  }

  public void testInsertionsIntoTransformationsDeletion() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append(String.valueOf(i + 1)).append('\n');
    }
    final GenericPatchApplier gap = new GenericPatchApplier(sb.toString(), Collections.emptyList());
    final List<String> strings = Arrays.asList("5", "7");
    // coincidence
    gap.putCutIntoTransformations(new TextRange(4, 6), new GenericPatchApplier.MyAppliedData(strings, true, true, true, GenericPatchApplier.ChangeType.REPLACE));
    final TreeMap<TextRange,GenericPatchApplier.MyAppliedData> transformations = gap.getTransformations();
    Assert.assertFalse(transformations.isEmpty());
    Assert.assertEquals(1, transformations.size());

    final Map.Entry<TextRange, GenericPatchApplier.MyAppliedData> entry = transformations.entrySet().iterator().next();
    final TextRange key = entry.getKey();
    Assert.assertTrue(key.getStartOffset() == 5 && key.getEndOffset() == 5);
    final List<String> list = entry.getValue().getList();
    Assert.assertTrue(list.size() == 0);
  }

  public void testInsertionsIntoTransformationsDeletion1() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append(String.valueOf(i + 1)).append('\n');
    }
    final GenericPatchApplier gap = new GenericPatchApplier(sb.toString(), Collections.emptyList());
    final List<String> strings = Arrays.asList("5", "6");
    // coincidence
    gap.putCutIntoTransformations(new TextRange(4, 7), new GenericPatchApplier.MyAppliedData(strings, true, true, true, GenericPatchApplier.ChangeType.REPLACE));
    final TreeMap<TextRange,GenericPatchApplier.MyAppliedData> transformations = gap.getTransformations();
    Assert.assertFalse(transformations.isEmpty());
    Assert.assertEquals(1, transformations.size());

    final Map.Entry<TextRange, GenericPatchApplier.MyAppliedData> entry = transformations.entrySet().iterator().next();
    final TextRange key = entry.getKey();
    Assert.assertTrue(key.getStartOffset() == 6 && key.getEndOffset() == 7);
    final List<String> list = entry.getValue().getList();
    Assert.assertTrue(list.size() == 0);
  }

  public void testInsertionsIntoTransformationsDeletion0() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append(String.valueOf(i + 1)).append('\n');
    }
    final GenericPatchApplier gap = new GenericPatchApplier(sb.toString(), Collections.emptyList());
    final List<String> strings = Arrays.asList("6", "7");
    // coincidence
    gap.putCutIntoTransformations(new TextRange(3, 6), new GenericPatchApplier.MyAppliedData(strings, true, true, true, GenericPatchApplier.ChangeType.REPLACE));
    final TreeMap<TextRange,GenericPatchApplier.MyAppliedData> transformations = gap.getTransformations();
    Assert.assertFalse(transformations.isEmpty());
    Assert.assertEquals(1, transformations.size());

    final Map.Entry<TextRange, GenericPatchApplier.MyAppliedData> entry = transformations.entrySet().iterator().next();
    final TextRange key = entry.getKey();
    Assert.assertTrue(key.getStartOffset() == 3 && key.getEndOffset() == 4);
    final List<String> list = entry.getValue().getList();
    Assert.assertTrue(list.size() == 0);
  }

  public void testBetterContextMatch() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\n0\n0\n3\n4\n5\n\n\n5454\n5345\n2\n3\n4\n5\n543543", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);

    final String after = gap.getAfter();
    Assert.assertEquals("0\n0\n0\n3\n4\n5\n\n\n5454\n5345\n2\na\nb\n543543", after);
  }

  public void testBetterContextMatch1() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "543543"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\n0\n0\n3\n4\n5\n\n\n5454\n5345\n3\n4\n5\n11\n543543", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);

    final String after = gap.getAfter();
    Assert.assertEquals("0\n0\n0\n3\n4\n5\n\n\n5454\n5345\na\nb\n11\n543543", after);
  }

  public void testBetterContextMatch2() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\n0\n0\n2\n3\n4\n5\n\n\n5454\n5345\n2\n3\n4\n5\n11\n543543", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);

    final String after = gap.getAfter();
    Assert.assertEquals("0\n0\n0\n2\n3\n4\n5\n\n\n5454\n5345\n2\na\nb\n11\n543543", after);
  }

  public void testFromSecondLineManySteps() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-1a"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-2a"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-3a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-3b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-4a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-4b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n-1\n4\n9\n8\n11" +
                                                            "\naaa\n2\n-2\n-3", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n-1\n4\n9\n8\n11\naaa\n2\n-1a\n-2a\n-3a\n-3b\n-4a\n-4b\n", after);
  }

  public void testFromSecondLineManySteps0() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-1*"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-1a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-1a*"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-2a"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-3a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-3b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "-4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-4a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "-4b"));

    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n-1\n-1*\n4\n9\n8\n11" +
                                                            "\naaa\n2\n-2\n-3\n-4", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\nfjsfsd\nqwhduhqwude\n\n2\n-1\n-1*\n4\n9\n8\n11\naaa\n2\n-1a\n-1a*\n-2a\n-3a\n-3b\n-4a\n-4b\n", after);
  }

  public void testMoreInsertParts() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "3c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "2r"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "2r"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "3a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "3c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "2r"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "8"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "9"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "10"));

    final GenericPatchApplier gap = new GenericPatchApplier("sdsad\nsdsad\n1c\n2c\n3c\n2r\n8\n9\n10\n", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("sdsad\nsdsad\n1c\n2c\n3c\n2r\n3a\n3c\n2r\n8\n9\n10\n", after);
  }

  public void testMoreDeletionParts() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "3c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "2r"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "2r"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3a"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3c"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "2r"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "8"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "9"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "10"));

    final GenericPatchApplier gap = new GenericPatchApplier("sdsad\nsdsad\n1c\n2c\n3c\n2r\n3a\n3c\n2r\n8\n9\n10\n", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("sdsad\nsdsad\n1c\n2c\n3c\n2r\n8\n9\n10\n", after);
  }

  public void testDoesNotMatchInTheEnd() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "6"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("0\nmmm\n2\n4\n7\n8\n11\naaa\n3\n3", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();

    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("0\nmmm\n2\n4\n7\n8\n11\naaa\n5\n6\n", after);
  }

  // actually didn't catch the previous version
  // nevertheless, should also pass
  public void testDoesNotMatchAtStart() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3="));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3-"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "6"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("3-\n0\nmmm\n2\n4\n7\n8\n11\naaa", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();

    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("5\n6\n0\nmmm\n2\n4\n7\n8\n11\naaa", after);
  }

  public void testOneLineInsertion() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    final PatchLine line = new PatchLine(PatchLine.Type.ADD, "5");
    line.setSuppressNewLine(true);
    patchHunk.addLine(line);

    final GenericPatchApplier gap = new GenericPatchApplier("", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertTrue(result);
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());

    final String after = gap.getAfter();
    //this hunk would be applied as not bounded, it would be written at first, thus we will ignore no new line
    Assert.assertEquals("5\n", after);
  }

  public void testOneLineBadInsertion() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    final PatchLine line = new PatchLine(PatchLine.Type.ADD, "5");
    line.setSuppressNewLine(true);
    patchHunk.addLine(line);

    final GenericPatchApplier gap = new GenericPatchApplier("7\n8\n5\n", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();

    final String after = gap.getAfter();
    Assert.assertEquals("5\n7\n8\n5\n", after);
  }

  public void testConflict1() {
    final PatchHunk patchHunk = new PatchHunk(1, 8, 1, 8);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3="));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    final GenericPatchApplier gap = new GenericPatchApplier("1\n2\n3=*7\n11\n12", Collections.singletonList(patchHunk));
    final boolean result = gap.execute();
    Assert.assertFalse(result);
    Assert.assertEquals(ApplyPatchStatus.FAILURE, gap.getStatus());

    gap.trySolveSomehow();

    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("1\n2\n5\n3=*7\n11\n12", after);
  }

  public void testAddAsFirst() {
    int[] beforeOfsetExpected = {1, 1};
    int[] afterOfsetExpected = {1, 2};
    final PatchHunk patchHunk = new PatchHunk(1, 4, 1, 5);
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "7"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "8"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "9"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));

    List<GenericPatchApplier.SplitHunk> hunks = GenericPatchApplier.SplitHunk.read(patchHunk);
    assertEquals(2, hunks.size());
    for (int i = 0; i < hunks.size(); i++) {
      assertEquals(beforeOfsetExpected[i], hunks.get(i).getStartLineBefore());
      assertEquals(afterOfsetExpected[i], hunks.get(i).getStartLineAfter());
    }
  }

  public void testOffsets() {
    final PatchHunk patchHunk1 = new PatchHunk(1, 2, 1, 1);
    patchHunk1.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk1.addLine(new PatchLine(PatchLine.Type.REMOVE, "2"));

    int[] beforeOfsetExpected = {3, 7, 9, 11};
    int[] afterOfsetExpected = {2, 6, 7, 9};
    final PatchHunk patchHunk2 = new PatchHunk(3, 12, 2, 11);
    patchHunk2.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.ADD, "6"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.CONTEXT, "7"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.REMOVE, "8"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.CONTEXT, "9"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.REMOVE, "10"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.ADD, "11"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.ADD, "13"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.CONTEXT, "14"));
    patchHunk2.addLine(new PatchLine(PatchLine.Type.CONTEXT, "15"));

    List<GenericPatchApplier.SplitHunk> hunks1 = GenericPatchApplier.SplitHunk.read(patchHunk1);
    List<GenericPatchApplier.SplitHunk> hunks2 = GenericPatchApplier.SplitHunk.read(patchHunk2);
    assertEquals(1, hunks1.size());
    assertEquals(4, hunks2.size());
    final GenericPatchApplier.SplitHunk splitHunk = hunks1.get(0);
    assertEquals(1, splitHunk.getStartLineBefore());
    assertEquals(1, splitHunk.getStartLineAfter());

    for (int i = 0; i < hunks2.size(); i++) {
      assertEquals(beforeOfsetExpected[i], hunks2.get(i).getStartLineBefore());
      assertEquals(afterOfsetExpected[i], hunks2.get(i).getStartLineAfter());
    }
  }


  public void testAlreadyApplied() {
    int[] beforeAppliedExpected = {2, 5, 6, 8};
    int[] endAppliedExpected = {4, 5, 7, 10};

    final PatchHunk patchHunk = new PatchHunk(2, 12, 2, 12);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "6"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "7"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "8"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "9"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "10"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "13"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "13"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "14"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "15"));

    GenericPatchApplier gap = new GenericPatchApplier("1\n2\n5\n6\n7\n9\n11\n12\n13\n13\n14\n", Collections.singletonList(patchHunk));
    assertTrue(gap.execute());
    List<AppliedTextPatch.AppliedSplitPatchHunk> appliedInfo = gap.getAppliedInfo();
     ContainerUtil.sort(appliedInfo, (o1, o2) -> Integer.compare(o1.getAppliedTo().start, o2.getAppliedTo().start));
    checkAppliedPositions(beforeAppliedExpected, endAppliedExpected, HunkStatus.ALREADY_APPLIED, appliedInfo);

    gap = new GenericPatchApplier("f\nd\n1768678\n2\n5\n6\n7\n9\n11\n12\n13\n13\n14\n", Collections.singletonList(patchHunk));
    assertTrue(gap.execute());
    appliedInfo = gap.getAppliedInfo();
    ContainerUtil.sort(appliedInfo, (o1, o2) -> Integer.compare(o1.getAppliedTo().start, o2.getAppliedTo().start));
    int[] beforeAppliedExpected2 = {4, 7, 8, 10};
    int[] endAppliedExpected2 = {6, 7, 9, 12};
    checkAppliedPositions(beforeAppliedExpected2, endAppliedExpected2, HunkStatus.ALREADY_APPLIED, appliedInfo);
  }

  public void testExactlyApplied() {
    int[] beforeAppliedExpected = {5, 8, 10, 12};
    int[] afterAppliedExpected = {7, 9, 11, 12};
    final PatchHunk patchHunk = new PatchHunk(2, 12, 2, 12);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "6"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "7"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "8"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "9"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "10"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "11"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "12"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "13"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "14"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "15"));

    final GenericPatchApplier gap =
      new GenericPatchApplier("0\n0werewrewr\n\n1\n2\n3\n4\n7\n8\n9\n10\n12\n14\n", Collections.singletonList(patchHunk));
    assertTrue(gap.execute());
    checkAppliedPositions(beforeAppliedExpected, afterAppliedExpected, HunkStatus.EXACTLY_APPLIED, gap.getAppliedInfo());
  }

  public void testMatchBeforeStartOffset() {
    final PatchHunk patchHunk = new PatchHunk(2, 9, 2, 12);
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "c2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "c2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "c3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a1"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "c4"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a2"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.ADD, "a3"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, ""));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "c5"));
    patchHunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "c6"));

    final GenericPatchApplier gap =
      new GenericPatchApplier("c2\nc2\nc3\nc4\n\nc5\nc6\nw\nw\n\nAn\nw\n\n\n", Collections.singletonList(patchHunk));
    gap.execute();
    Assert.assertEquals(ApplyPatchStatus.SUCCESS, gap.getStatus());
    final String after = gap.getAfter();
    Assert.assertEquals("c2\nc2\nc3\na1\nc4\na2\na3\n\nc5\nc6\nw\nw\n\nAn\nw\n\n\n", after);
  }

  private static void checkAppliedPositions(int[] beforeAppliedExpected,
                                            int[] endAppliedExpected, HunkStatus expectedStatus,
                                            List<AppliedTextPatch.AppliedSplitPatchHunk> appliedInfo) {
    for (int i = 0; i < appliedInfo.size(); i++) {
      final AppliedTextPatch.AppliedSplitPatchHunk hunk = appliedInfo.get(i);
      assertEquals(beforeAppliedExpected[i], hunk.getAppliedTo().start);
      assertEquals(endAppliedExpected[i], hunk.getAppliedTo().end);
      assertEquals(expectedStatus, hunk.getStatus());
    }
  }
}
