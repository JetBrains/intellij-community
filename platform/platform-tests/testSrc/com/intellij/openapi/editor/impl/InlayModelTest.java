// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InlayModelTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testInlineInlaysAtSameOffsetAreSortedByRelationToPrecedingText() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      Inlay<?> relatedToFollowingText = myFixture.addInlay(1, false);
      Inlay<?> relatedToPrecedingText = myFixture.addInlay(1, true);

      List<Inlay<?>> inlays = myFixture.getEditor().getInlayModel().getInlineElementsInRange(1, 1);
      assertEquals(2, inlays.size());
      assertEquals(relatedToFollowingText, inlays.get(0));
      assertFalse(inlays.get(0).isRelatedToPrecedingText());
      assertEquals(relatedToPrecedingText, inlays.get(1));
      assertTrue(inlays.get(1).isRelatedToPrecedingText());
    });
  }

  @Test
  public void testWidestBlockInlayIsNullIfNoInlayExists() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      assertNull(
        getWidestVisibleBlockInlay(),
        "no widest block inlay expected if no inlay exists"
      );
    });
  }

  @Test
  public void testWidestBlockInlayIsNullIfInlayFolded() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.addBlockInlay(1, true, 10);
      myFixture.toggleFoldRegionState(myFixture.addFoldRegion(0, 2, ""), false);
      assertNull(
        getWidestVisibleBlockInlay(),
        "no widest block inlay expected if no visible inlay exists"
      );
    });
  }

  @Test
  public void testWidestBlockInlayWithOneInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      Inlay<?> expected = myFixture.addBlockInlay(0, true, 10);
      Inlay<?> actual = getWidestVisibleBlockInlay();
      assertEquals(expected, actual);
    });
  }

  @Test
  public void testWidestBlockInlayWithTwoInlays() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.addBlockInlay(0, true, 10);
      Inlay<?> expected = myFixture.addBlockInlay(1, true, 20);
      Inlay<?> actual = getWidestVisibleBlockInlay();
      assertEquals(expected, actual);
    });
  }

  @Test
  public void testWidestBlockInlayWithTwoInlaysReversed() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      Inlay<?> expected = myFixture.addBlockInlay(0, true, 20);
      myFixture.addBlockInlay(1, true, 10);
      Inlay<?> actual = getWidestVisibleBlockInlay();
      assertEquals(expected, actual);
    });
  }

  private @Nullable Inlay<?> getWidestVisibleBlockInlay() {
    return ((InlayModelImpl) myFixture.getEditor().getInlayModel()).getWidestVisibleBlockInlay();
  }
}
