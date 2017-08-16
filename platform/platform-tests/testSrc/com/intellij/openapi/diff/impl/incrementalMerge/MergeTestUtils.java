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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Assertion;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MergeTestUtils {
  private final Project myProject;
  private final ArrayList<Editor> myEditorsToDispose = new ArrayList<>();
  private static final Assertion CHECK = new Assertion();

  public static class Range {
    private final String myId;
    private final TextRange myRange;

    Range(String id, TextRange range) {
      myId = id;
      myRange = range;
    }

    @Override
    public String toString() {
      return myId + " " + myRange;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Range range = (Range)o;

      if (!myId.equals(range.myId)) return false;
      if (!myRange.equals(range.myRange)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myId.hashCode();
      result = 31 * result + myRange.hashCode();
      return result;
    }
  }

  public MergeTestUtils(Project project) {
    myProject = project;
  }

  public static void checkMarkup(Editor editor, Range[] expected) {
    checkMarkup(editor, expected, null);
  }

  public static void checkMarkup(Editor editor, Range[] expected, @Nullable Range[] expectedApplied) {
    List<RangeHighlighter> allHighlighters = Arrays.asList(editor.getMarkupModel().getAllHighlighters());
    List<RangeHighlighter> changes = ContainerUtil.findAll(allHighlighters, CHANGE_HIGHLIGHTERS);
    List<RangeHighlighter> appliedChanges = ContainerUtil.findAll(allHighlighters, APPLIED_CHANGE_HIGHLIGHTERS);
    try {
      checkMarkup(editor, changes, expected);
      if (expectedApplied != null) {
        checkMarkup(editor, appliedChanges, expectedApplied);
      }
    } catch(AssertionFailedError e) {
      List<Range> ranges = ContainerUtil.map(allHighlighters, new HighlighterToRangeConvertor(editor));
      CHECK.enumerate(ranges);
      throw e;
    }
  }

  private static void checkMarkup(Editor editor, List<RangeHighlighter> changes, Range[] expected) {
    Function<RangeHighlighter, Range> toRangeConvertor = new HighlighterToRangeConvertor(editor);
    List<Range> actualRanges = ContainerUtil.map(changes, toRangeConvertor);
    Assertion.compareUnordered(expected, actualRanges);

    for (RangeHighlighter highlighter : changes) {
      if (highlighter.getStartOffset() == highlighter.getEndOffset()) continue;
      Assert.assertEquals(Color.GRAY, highlighter.getLineSeparatorColor());
      Assert.assertEquals(SeparatorPlacement.TOP, highlighter.getLineSeparatorPlacement());
      List<RangeHighlighter> allHighlighters = Arrays.asList(editor.getMarkupModel().getAllHighlighters());
      RangeHighlighter bottomLine = findBottomHighlighter(highlighter, allHighlighters);
      Assert.assertNotNull(String.format("The bottom line of %s is null!", toRangeConvertor.fun(highlighter)), bottomLine);
      Assert.assertEquals(Color.GRAY, bottomLine.getLineSeparatorColor());
    }
  }

  @Nullable
  private static RangeHighlighter findBottomHighlighter(RangeHighlighter highlighter, List<RangeHighlighter> allHighlighters) {
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (highlighter.getDocument().getCharsSequence().charAt(endOffset - 1) == '\n') endOffset--;
    for (RangeHighlighter rangeHighlighter : allHighlighters) {
      if (rangeHighlighter.getStartOffset() != startOffset || rangeHighlighter.getEndOffset() != endOffset) continue;
      if (!SeparatorPlacement.BOTTOM.equals(rangeHighlighter.getLineSeparatorPlacement())) continue;
      return rangeHighlighter;
    }
    return null;
  }

  public static Range ins(int start, int length) {
    return new Range(DiffColors.DIFF_INSERTED.getExternalName(), createRange(start, length));
  }

  protected void tearDown() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    for (Editor editor : myEditorsToDispose) {
      editorFactory.releaseEditor(editor);
    }
  }

  public Editor[] createEditors(Document[] documents) {
    Editor[] editors = new Editor[documents.length];
    for (int i = 0; i < documents.length; i++) {
      Document document = documents[i];
      editors[i] = createEditor(document);
    }
    return editors;
  }

  public Editor createEditor(Document document) {
    Editor editor = EditorFactory.getInstance().createEditor(document);
    myEditorsToDispose.add(editor);
    return editor;
  }

  private static final String[] POSSIBLE_ATTRIBUTES =
    {DiffColors.DIFF_INSERTED.getExternalName(),
      DiffColors.DIFF_DELETED.getExternalName(),
      DiffColors.DIFF_MODIFIED.getExternalName(),
      DiffColors.DIFF_CONFLICT.getExternalName()};

  private static final Condition<RangeHighlighter> COMMON_CHANGE_HIGHLIGHTERS =
    highlighter -> {
      if (!highlighter.isValid()) return false;
      if (highlighter.getLineSeparatorPlacement() == SeparatorPlacement.BOTTOM) return false;
      GutterIconRenderer iconRenderer = (GutterIconRenderer)highlighter.getGutterIconRenderer();
      if (highlighter.getTextAttributes() == null && highlighter.getLineSeparatorColor() == null &&
          iconRenderer != null && iconRenderer.getClickAction() != null) return false;
      return true;
    };

  private static final Condition<RangeHighlighter> CHANGE_HIGHLIGHTERS =
    highlighter -> COMMON_CHANGE_HIGHLIGHTERS.value(highlighter) && !isAppliedChange(highlighter);

  private static final Condition<RangeHighlighter> APPLIED_CHANGE_HIGHLIGHTERS =
    highlighter -> COMMON_CHANGE_HIGHLIGHTERS.value(highlighter) && isAppliedChange(highlighter);

  private static boolean isAppliedChange(RangeHighlighter highlighter) {
    Color stripeMarkColor = highlighter.getErrorStripeMarkColor();
    return stripeMarkColor != null && stripeMarkColor.getAlpha() == ChangeHighlighterHolder.APPLIED_CHANGE_TRANSPARENCY;
  }

  public static Document createRODocument(String text) {
    Document document = createDocument(text);
    document.setReadOnly(true);
    return document;
  }

  public static Document createDocument(String text) {
    return EditorFactory.getInstance().createDocument(text);
  }

  public static Range del(int start, int length) {
    return new Range(DiffColors.DIFF_DELETED.getExternalName(), createRange(start, length));
  }

  private static TextRange createRange(int start, int length) {
    return new TextRange(start, start + length);
  }

  public static Range mod(int start, int length) {
    return new Range(DiffColors.DIFF_MODIFIED.getExternalName(), createRange(start, length));
  }

  public static Range conf(int start, int length) {
    return new Range(DiffColors.DIFF_CONFLICT.getExternalName(), createRange(start, length));
  }

  public void insertString(final Document document, final int offset, final String text) {
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myProject, () -> document.insertString(offset, text), null, null));
  }

  private static class ColorToIdConvertor implements Convertor<Color, String> {
    private final Editor myEditor;

    public ColorToIdConvertor(Editor editor) {
      myEditor = editor;
    }

    @Override
    public String convert(Color backgroundColor) {
      for (String key : POSSIBLE_ATTRIBUTES) {
        if (!backgroundColor.equals(getAttributes(key).getBackgroundColor())) continue;
        return key;
      }
      return null;
    }

    private TextAttributes getAttributes(String key) {
      return myEditor.getColorsScheme().getAttributes(TextAttributesKey.find(key));
    }
  }

  private static class HighlighterToRangeConvertor implements Function<RangeHighlighter, Range> {
    private final Convertor<Color, String> myColorToId;

    public HighlighterToRangeConvertor(Editor editor) {
      myColorToId = new ColorToIdConvertor(editor);
    }

    @Override
    public Range fun(RangeHighlighter highlighter) {
      TextAttributes textAttributes = highlighter.getTextAttributes();
      Color color;
      if (textAttributes != null) color = textAttributes.getBackgroundColor();
      else color = highlighter.getLineSeparatorColor();
      String id;
      if (Color.GRAY.equals(color)) id = "lineSeparator";
      else id = color != null ? myColorToId.convert(color) : highlighter.getGutterIconRenderer().getTooltipText();
      TextRange range = TextRange.create(highlighter);
      return new Range(id, range);
    }
  }

}
