// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.ex.SoftWrapChangeListener;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.editor.colors.FontPreferencesTest.getExistingNonDefaultFontName;
import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SoftWrapTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testCollapsedRegionWithLongPlaceholderAtLineStart1() {
    myFixture.runTest(() -> {
      doTestSoftWraps(10, "<fold text='veryVeryVeryLongPlaceholder'>foo</fold>");
    });
  }

  @Test
  public void testCollapsedRegionWithLongPlaceholderAtLineStart2() {
    myFixture.runTest(() -> {
      doTestSoftWraps(10, "<fold text='veryVeryVeryLongPlaceholder'>foo</fold><wrap>bar");
    });
  }

  @Test
  public void testCollapsedRegionWithLongPlaceholderAtLineStart3() {
    myFixture.runTest(() -> {
      doTestSoftWraps(10, "<fold text='veryVeryVeryLongPlaceholder'>foo</fold>\nvery long <wrap>text");
    });
  }

  @Test
  public void testNoWrapInsideFoldRegion() {
    myFixture.runTest(() -> {
      doTestSoftWraps(10, "start<fold text='.....'>text with space</fold><wrap>end");
    });
  }

  @Test
  public void testChangeOfEffectiveFontFamiliesOnlyResetsSoftWraps() {
    myFixture.runTest(() -> {
      myFixture.init("some text that is long enough to wrap", PlainTextFileType.INSTANCE);
      Editor editor = myFixture.getEditor();
      EditorTestUtil.configureSoftWraps(editor, 10);
      String fontFamily = getExistingNonDefaultFontName();
      var first = new FontPreferencesImpl();
      first.register(fontFamily, 14);
      editor.getColorsScheme().setFontPreferences(first);

      var changes = new AtomicInteger();
      ((SoftWrapModelEx)editor.getSoftWrapModel()).addSoftWrapChangeListener(new SoftWrapChangeListener() {
        @Override
        public void softWrapsChanged() {
          changes.incrementAndGet();
        }

        @Override
        public void recalculationEnds() {
        }
      });
      var second = new FontPreferencesImpl();
      first.copyTo(second);
      second.setEffectiveFontFamilies(List.of(fontFamily, FontPreferences.DEFAULT_FONT_NAME));
      editor.getColorsScheme().setFontPreferences(second);

      assertTrue(changes.get() > 0, "Soft wraps were not reset");
    });
  }

  private static final String TAGS_PATTERN = "(<fold(\\stext='([^']*)')?>)|(</fold>)|<wrap>";

  private void doTestSoftWraps(int wrapWidth, String text) {
    List<MyFoldRegion> foldRegions = new ArrayList<>();
    List<Integer> wrapPositions = new ArrayList<>();
    int foldInsertPosition = 0;
    int pos = 0;
    int docPos = 0;
    Matcher matcher = Pattern.compile(TAGS_PATTERN).matcher(text);
    StringBuilder cleanedText = new StringBuilder();
    while(matcher.find()) {
      cleanedText.append(text, pos, matcher.start());
      docPos += matcher.start() - pos;
      pos = matcher.end();
      if (matcher.group(1) != null) {       // <fold>
        foldRegions.add(foldInsertPosition++, new MyFoldRegion(docPos, matcher.group(3), matcher.group(2) != null));
      }
      else if (matcher.group(4) != null) {  // </fold>
        assertTrue(foldInsertPosition > 0, "Misplaced closing fold marker tag: " + text);
        foldRegions.get(--foldInsertPosition).endPos = docPos;
      }
      else {                                // <wrap>
        wrapPositions.add(docPos);
      }
    }
    assertEquals(0, foldInsertPosition, "Missing closing fold marker tag: " + text);
    cleanedText.append(text.substring(pos));

    myFixture.init(cleanedText.toString(), PlainTextFileType.INSTANCE);

    for (MyFoldRegion region : foldRegions) {
      FoldRegion r = myFixture.addFoldRegion(region.startPos, region.endPos, region.placeholder);
      if (region.collapse) {
        myFixture.toggleFoldRegionState(r, false);
      }
    }

    EditorTestUtil.configureSoftWraps(myFixture.getEditor(), wrapWidth);

    List<Integer> actualWrapPositions = new ArrayList<>();
    Editor editor = myFixture.getEditor();
    for (SoftWrap wrap : editor.getSoftWrapModel().getSoftWrapsForRange(0, editor.getDocument().getTextLength())) {
      actualWrapPositions.add(wrap.getStart());
    }
    assertEquals(wrapPositions, actualWrapPositions, "Wrong wrap positions");
  }

  private static final class MyFoldRegion {
    private final int startPos;
    private int endPos;
    private final String placeholder;
    private final boolean collapse;

    private MyFoldRegion(int pos, String placeholder, boolean collapse) {
      this.startPos = pos;
      this.placeholder = placeholder;
      this.collapse = collapse;
    }
  }
}
