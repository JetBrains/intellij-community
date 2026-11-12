// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class MarkupModelStressTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private static final Logger LOG = Logger.getInstance(MarkupModelStressTest.class);

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  private static final int RANDOM_ITERATIONS = 10_000;
  private static final int MAX_CHARS_PER_OPERATION = 10;
  private static final Long SEED_OVERRIDE = null; // set non-null value to run with a specific seed
  static final GutterIconRenderer DUMMY_GUTTER_ICON_RENDERER = new DummyGutterIconRenderer();

  private final List<? extends Runnable> ourActions = Arrays.asList(new AddHighlighter(),
                                                                    new RemoveHighlighter(),
                                                                    new UpdateHighlighter(),
                                                                    new AddCharacters(),
                                                                    new RemoveCharacters(),
                                                                    new MoveCharacters());
  private final Random myRandom = new Random() {{
    //noinspection ConstantValue
    setSeed(mySeed = SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE);
  }};
  private long mySeed;

  @Test
  public void testRenderedFlagConsistencyByRandomOperations() {
    myFixture.runTest(() -> {
      LOG.debug("Seed is " + mySeed);
      int i = 0;
      try {
        myFixture.initText("");
        for (i = 0; i < RANDOM_ITERATIONS; i++) {
          ourActions.get(myRandom.nextInt(ourActions.size())).run();
          validateState();
        }
      }
      catch (Throwable t) {
        String message = "Failed when run with seed=" + mySeed + " in iteration " + i;
        System.err.println(message);
        throw new RuntimeException(message, t);
      }
    });
  }

  private void validateState() {
    MarkupModelEx markupModel = ((EditorEx)myFixture.getEditor()).getMarkupModel();
    List<RangeHighlighterEx> list1;
    try (MarkupIterator<RangeHighlighterEx> it1 = markupModel.overlappingIterator(0, Integer.MAX_VALUE)) {
      list1 = ContainerUtil.collect(it1, h -> h.isRenderedInGutter());
    }

    List<RangeHighlighterEx> list2;
    try (MarkupIterator<RangeHighlighterEx> it2 = FilteringMarkupIterator.create(markupModel.overlappingIterator(0, Integer.MAX_VALUE),
                                                                                h -> h.isRenderedInGutter())) {
      list2 = ContainerUtil.collect(it2);
    }
    assertEquals(list1, list2);
  }

  private class AddHighlighter implements Runnable {
    @Override
    public void run() {
      int bound = myFixture.getEditor().getDocument().getTextLength() + 1;
      int offset1 = myRandom.nextInt(bound);
      int offset2 = myRandom.nextInt(bound);
      myFixture.getEditor().getMarkupModel().addRangeHighlighter(null, Math.min(offset1, offset2), Math.max(offset1, offset2), 0,
                                                       HighlighterTargetArea.EXACT_RANGE);
    }
  }

  private class RemoveHighlighter implements Runnable {
    @Override
    public void run() {
      RangeHighlighter[] highlighters = myFixture.getEditor().getMarkupModel().getAllHighlighters();
      int size = highlighters.length;
      if (size > 0) {
        highlighters[myRandom.nextInt(size)].dispose();
      }
    }
  }

  private class UpdateHighlighter implements Runnable {
    @Override
    public void run() {
      RangeHighlighter[] highlighters = myFixture.getEditor().getMarkupModel().getAllHighlighters();
      int size = highlighters.length;
      if (size > 0) {
        RangeHighlighter h = highlighters[myRandom.nextInt(size)];
        h.setGutterIconRenderer(h.getGutterIconRenderer() == null ? DUMMY_GUTTER_ICON_RENDERER : null);
      }
    }
  }

  private class AddCharacters implements Runnable {
    @Override
    public void run() {
      Document document = myFixture.getEditor().getDocument();
      int offset = myRandom.nextInt(document.getTextLength() + 1);
      myFixture.runWriteCommand(() -> document.insertString(offset, StringUtil.repeat(" ", myRandom.nextInt(MAX_CHARS_PER_OPERATION) + 1)));
    }
  }

  private class RemoveCharacters implements Runnable {
    @Override
    public void run() {
      Document document = myFixture.getEditor().getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int offset = myRandom.nextInt(textLength);
      myFixture.runWriteCommand(
        () -> document.deleteString(offset, Math.min(textLength, offset + 1 + myRandom.nextInt(MAX_CHARS_PER_OPERATION))));
    }
  }

  private class MoveCharacters implements Runnable {
    @Override
    public void run() {
      DocumentEx document = (DocumentEx)myFixture.getEditor().getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int startOffset = myRandom.nextInt(textLength);
      int endOffset = Math.min(textLength, startOffset + 1 + myRandom.nextInt(MAX_CHARS_PER_OPERATION));
      int targetOffset = myRandom.nextInt(textLength + 1);
      if (targetOffset < startOffset || targetOffset > endOffset) {
        myFixture.runWriteCommand(() -> document.moveText(startOffset, endOffset, targetOffset));
      }
    }
  }

  private static class DummyGutterIconRenderer extends GutterIconRenderer {
    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      return EmptyIcon.ICON_0;
    }
  }
}
