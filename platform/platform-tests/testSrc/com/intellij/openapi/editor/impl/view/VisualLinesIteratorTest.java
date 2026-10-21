/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.view;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class VisualLinesIteratorTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testWrapAfterFolding() {
    myFixture.runTest(() -> {
      myFixture.initText("abcdefghij");
      myFixture.addCollapsedFoldRegion(1, 8, "...");
      myFixture.configureSoftWraps(4);
    
      myFixture.verifySoftWrapPositions(8);
    
      VisualLinesIterator it = createIterator(0);
      assertFalse(it.atEnd());
      assertEquals(0, it.getVisualLineStartOffset());
      assertEquals(0, it.getStartFoldingIndex());
      it.advance();
      assertFalse(it.atEnd());
      assertEquals(8, it.getVisualLineStartOffset());
      assertEquals(1, it.getStartFoldingIndex());
    });
  }
  
  @Test
  public void testWrapAtFoldedLineBreak() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef");
      myFixture.addCollapsedFoldRegion(3, 4, "...");
      myFixture.configureSoftWraps(6);
    
      myFixture.verifySoftWrapPositions(4);
      VisualLinesIterator it = createIterator(0);
      assertFalse(it.atEnd());
      it.advance();
      assertFalse(it.atEnd());
      it.advance();
      assertTrue(it.atEnd());
    });
  }

  @Test
  public void testEmptyDocument() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      VisualLinesIterator it = createIterator(0);
      assertFalse(it.atEnd());
      assertEquals(0, it.getVisualLine());
      assertEquals(0, it.getVisualLineStartOffset());
      assertEquals(0, it.getVisualLineEndOffset());
      assertEquals(0, it.getStartLogicalLine());
      assertEquals(0, it.getEndLogicalLine());
      it.advance();
      assertTrue(it.atEnd());
    });
  }

  private VisualLinesIterator createIterator(int startVisualLine) {
    return new VisualLinesIterator((EditorImpl)myFixture.getEditor(), startVisualLine);
  }
}