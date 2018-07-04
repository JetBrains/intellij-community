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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DiffFilesTest extends TestSuite {
  private static final String RESULT1_EXT = ".lines1";

  public DiffFilesTest() {
    File allTestsDir = BaseDiffTestCase.getDirectory();
    HashMap<ComparisonPolicy, String> policies = MyIdeaTestCase.ourPolicyToDirectory;
    for (ComparisonPolicy comparisonPolicy : policies.keySet()) {
      File dir = new File(allTestsDir, policies.get(comparisonPolicy));
      if (!dir.exists()) continue;
      File[] files = dir.listFiles();
      if (files == null) return;
      for (File file : files) {
        String nameWithExt = file.getName();
        if (StringUtil.startsWithChar(nameWithExt, '_')) continue;
        if (!nameWithExt.endsWith(RESULT1_EXT)) continue;
        String name = nameWithExt.substring(0, nameWithExt.length() - RESULT1_EXT.length());
        addTest(new MyIdeaTestCase(name, comparisonPolicy) {
        });
      }
    }
  }

  public static Test suite() {
    return new DiffFilesTest();
  }

  public abstract static class MyIdeaTestCase extends BaseDiffTestCase {
    private static final HashMap<ComparisonPolicy, String> ourPolicyToDirectory = new HashMap<>();
    private final File myResultFile1;
    private final File myResultFile2;
    private String myName;
    private final ComparisonPolicy myTestingPolicy;

    public MyIdeaTestCase(String name, ComparisonPolicy policy) {
      this(name + ".1", name + ".2", name + RESULT1_EXT, name + ".lines2", policy);
      myName = name;
    }

    private MyIdeaTestCase(String file1, String file2, String result1, String result2, ComparisonPolicy policy) {
      myTestingPolicy = policy;
      String directory = ourPolicyToDirectory.get(myTestingPolicy) + File.separatorChar;
      setFile1(directory + file1);
      setFile2(directory + file2);
      myResultFile1 = getTestFile(result1);
      myResultFile2 = getTestFile(result2);
      setName("test");
    }

    private File getTestFile(String name) {
      return new File(getFile(ourPolicyToDirectory.get(myTestingPolicy)), name);
    }

    @Override
    public String getName() {
      return myName != null ? myName : super.getName();
    }

    @Override
    protected void resetAllFields() {
      // Do nothing otherwise myName will be nulled out before getName() is called.
    }

    public void test() throws IOException {
      DiffPanelImpl diffPanel = createDiffPanel(null, myProject, false);
      diffPanel.setComparisonPolicy(myTestingPolicy);
      String content1 = content1();
      String content2 = content2();
      setContents(diffPanel, content1, content2);
      Editor editor1 = BaseDiffTestCase.getEditor1(diffPanel);
      checkResult("Editor 1", myResultFile1, editor1);
      Editor editor2 = BaseDiffTestCase.getEditor2(diffPanel);
      checkResult("Editor 2", myResultFile2, editor2);
      checkTextEqual(content1, editor1);
      checkTextEqual(content2, editor2);
    }

    private void checkResult(String message, File resultFile, Editor editor1) throws IOException {
      String expected = readFile(resultFile);
      String actual = process(editor1);
      List<String> exp = StringUtil.split(expected, "\n");
      Collections.sort(exp);
      List<String> act = StringUtil.split(actual, "\n");
      Collections.sort(act);
      if (!exp.equals(act)) {
        throw new FileComparisonFailure(message, StringUtil.join(act, "\n"), StringUtil.join(act, "\n"), resultFile.getAbsolutePath());
      }
    }

    protected String process(Editor editor) {
      RangeHighlighter[] highlighters1 = editor.getMarkupModel().getAllHighlighters();
      StringBuffer result = new StringBuffer();
      for (RangeHighlighter highlighter : highlighters1) {
        HighlighterTargetArea targetArea = highlighter.getTargetArea();
        if (targetArea == HighlighterTargetArea.EXACT_RANGE) {
          processRange(highlighter, result, editor);
        }
        else if (targetArea == HighlighterTargetArea.LINES_IN_RANGE) {
          if (highlighter.getLineSeparatorPlacement() == null && highlighter.getLayer() == CurrentLineMarker.LAYER) {
            continue;
          }
          processLine(highlighter, result, editor);
        }
        else {
          fail("Unknown highlighter: " + String.valueOf(targetArea));
        }
        result.append('\n');
      }
      return result.toString();
    }

    private static void processLine(RangeHighlighter highlighter, StringBuffer result, Editor editor) {
      SeparatorPlacement placement = highlighter.getLineSeparatorPlacement();
      if (highlighter.getLineSeparatorColor() != null) {
        result.append("L");
      }
      else {
        result.append("N");
      }
      if (SeparatorPlacement.TOP.equals(placement)) {
        result.append('T');
      }
      else if (SeparatorPlacement.BOTTOM.equals(placement)) {
        result.append('B');
      }
      else {
        fail("Unknown placement: " + String.valueOf(placement) + "(" + result + ")");
      }
      int line = editor.getDocument().getLineNumber(highlighter.getStartOffset());
      result.append(' ');
      result.append(line);
    }

    private static void processRange(RangeHighlighter highlighter, StringBuffer result, Editor editor) {
      int startOffset = highlighter.getStartOffset();
      int endOffset = highlighter.getEndOffset();
      if (startOffset == endOffset) {
        assertEquals(EffectType.BOXED, highlighter.getTextAttributes().getEffectType());
      }
      else {
        assertTrue(startOffset + "<" + endOffset, startOffset < endOffset);
      }
      TextAttributes textAttributes = highlighter.getTextAttributes();
      Color originalColorOfInlineWrapper = getOriginalColor(textAttributes, editor);

      if (isAttributesKey(textAttributes, DiffColors.DIFF_INSERTED, editor)) {
        result.append("I");
        appendOffsets(result, startOffset, endOffset);
      }
      else if (isAttributesKey(textAttributes, DiffColors.DIFF_DELETED, editor)) {
        result.append("D");
        appendOffsets(result, startOffset, endOffset);
      }
      else if (isAttributesKey(textAttributes, DiffColors.DIFF_MODIFIED, editor)) {
        result.append("C");
        appendOffsets(result, startOffset, endOffset);
      }
      else if (colorsEqual(getBgColor(DiffColors.DIFF_INSERTED, editor), originalColorOfInlineWrapper) ||
               colorsEqual(getBgColor(DiffColors.DIFF_DELETED, editor), originalColorOfInlineWrapper) ||
               colorsEqual(getBgColor(DiffColors.DIFF_MODIFIED, editor), originalColorOfInlineWrapper)) {
        // ignoring inline wrapper highlighters
      }
      else if (textAttributes.getEffectType() == EffectType.BOXED) {
        result.append("B");
        appendOffsets(result, startOffset, endOffset);
      }
      else {
        fail(textAttributes.toString());
      }
    }

    private static void appendOffsets(StringBuffer result, int startOffset, int endOffset) {
      result.append(' ');
      result.append(startOffset + "-" + endOffset);
    }

    private static Color getBgColor(TextAttributesKey key, Editor editor) {
      EditorColorsScheme colorsScheme = editor.getColorsScheme();
      return colorsScheme.getAttributes(key).getBackgroundColor();
    }

    private static boolean isAttributesKey(TextAttributes textAttributes, TextAttributesKey key, Editor editor) {
      EditorColorsScheme colorsScheme = editor.getColorsScheme();
      return textAttributes.equals(colorsScheme.getAttributes(key));
    }

    static {
      ourPolicyToDirectory.put(ComparisonPolicy.DEFAULT, "default");
      ourPolicyToDirectory.put(ComparisonPolicy.TRIM_SPACE, "trim");
      ourPolicyToDirectory.put(ComparisonPolicy.IGNORE_SPACE, "noSpaces");
    }
  }

  /**
   * The reverse of the formula in {@link TextDiffType#getMiddleColor(Color, Color, double)} to let the test
   * identify and ignore highlighters of inline diff wrappers.
   */
  @Nullable
  private static Color getOriginalColor(@Nullable TextAttributes highlighterAttrs, @NotNull Editor editor) {
    if (highlighterAttrs == null) {
      return null;
    }

    try {
      Color fg = highlighterAttrs.getBackgroundColor();
      Color bg = ((EditorImpl)editor).getBackgroundColor();

      int b = reverseMiddle(fg.getBlue(), bg.getBlue());
      int g = reverseMiddle(fg.getGreen(), bg.getGreen());
      int r = reverseMiddle(fg.getRed(), bg.getRed());

      return new Color(r, g, b);
    }
    catch (IllegalArgumentException e) {
      // some other color (not produced by the "get-middle-color" formula) when provided to the reverse formula
      // may produce invalid values (more than 255).
      return null;
    }
  }

  // reverseMiddle calculation may be not accurate enough => allow eps = 1
  private static boolean colorsEqual(@NotNull Color color1, @Nullable Color color2) {
    if (color2 == null) {
      return false;
    }

    int eps = 1;
    return Math.abs(color1.getRed() - color2.getRed()) <= eps &&
           Math.abs(color1.getGreen() - color2.getGreen()) <= eps &&
           Math.abs(color1.getBlue() - color2.getBlue()) <= eps;
  }

  private static int reverseMiddle(int fb, int bb) {
    return (5*fb - 3*bb) / 2;
  }

}
