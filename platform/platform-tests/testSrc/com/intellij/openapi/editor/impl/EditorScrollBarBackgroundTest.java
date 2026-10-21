// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.components.ScrollBarPainter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollBar;
import java.awt.Color;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class EditorScrollBarBackgroundTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testScrollBarBackgroundFollowsEditorBackground() {
    myFixture.runTest(() -> {
      myFixture.initText("some text");
      EditorEx editor = (EditorEx)myFixture.getEditor();
      JScrollBar verticalScrollBar = editor.getScrollPane().getVerticalScrollBar();

      Color editorBackground = new Color(0x123456);
      editor.setBackgroundColor(editorBackground);
      // The editor scroll bar background follows the editor background instead of the LAF default ScrollBar.background.
      assertEquals(editorBackground, EditorColorsUtil.getColor(verticalScrollBar, ScrollBarPainter.BACKGROUND));

      // It keeps tracking subsequent editor background changes.
      Color newBackground = new Color(0x654321);
      editor.setBackgroundColor(newBackground);
      assertEquals(newBackground, EditorColorsUtil.getColor(verticalScrollBar, ScrollBarPainter.BACKGROUND));
    });
  }
}
