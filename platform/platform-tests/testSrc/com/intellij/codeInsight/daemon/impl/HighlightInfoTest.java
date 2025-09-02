// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.xml.util.XmlStringUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

public class HighlightInfoTest extends TestCase {
  public void testHtmlTooltipWithDescription() {
    String description = "description with <>";
    String tooltip = XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(description)
                                              + "<br>"
                                              + "<a href ='#navigation//some/path.txt:0'>"
                                              + XmlStringUtil.escapeString("hint with <>")
                                              + "</a>");

    assertTooltipValid(description, tooltip);
  }

  public void testHtmlTooltipWithoutDescription() {
    String description = "description with <>";
    String tooltip = XmlStringUtil.wrapInHtml("Different description: "
                                              + "<br>"
                                              + "<a href ='#navigation//some/path.txt:0'>"
                                              + XmlStringUtil.escapeString("hint with <>")
                                              + "</a>");

    assertTooltipValid(description, tooltip);
  }

  private static void assertTooltipValid(@NotNull String description, @NotNull String tooltip) {
    HighlightInfo newInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(1, 2)
      .description(description).escapedToolTip(tooltip).createUnconditionally();

    assertEquals(tooltip, newInfo.getToolTip());
  }

  public void testBuilderMustNotAcceptDuplicates() {
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.description("X"));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.descriptionAndTooltip("X"));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.endOfLine());
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.escapedToolTip("X"));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.fileLevelAnnotation());
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.group(2));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.gutterIconRenderer(new GutterIconRenderer() {
      @Override
      public boolean equals(Object obj) {
        return false;
      }

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public @NotNull Icon getIcon() {
        return EmptyIcon.ICON_0;
      }
    }));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.navigationShift(2));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.needsUpdateOnTyping(true));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.problemGroup(new ProblemGroup() {
      @Override
      public String getProblemName() {
        return "";
      }
    }));
    UsefulTestCase.assertThrows(IllegalArgumentException.class, ()->{
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).createUnconditionally();// no range
    });
    UsefulTestCase.assertThrows(IllegalArgumentException.class, ()->{
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(1,2).range(1,2);
    });
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.severity(HighlightSeverity.ERROR));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.textAttributes(new TextAttributes()));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.textAttributes(CodeInsightColors.LINE_NONE_COVERAGE));
    checkThrowsWhenCalledTwiceOrAfterCreate(builder -> builder.unescapedToolTip("X"));
    UsefulTestCase.assertThrows(IllegalArgumentException.class, ()->{
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(2,5);
      builder.createUnconditionally();
      builder.createUnconditionally();
    });
  }

  public void testBuilderFromCopyMustAcceptDuplicates() {
    HighlightInfo.Builder builder =
    HighlightInfo.newHighlightInfo(HighlightInfoType.ELEMENT_UNDER_CARET_WRITE)
      .range(2,3)
      .fileLevelAnnotation()
      .textAttributes(CodeInsightColors.LINE_FULL_COVERAGE)
      .severity(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING)
      .group(-333)
      .descriptionAndTooltip("D")
      .endOfLine()
      .navigationShift(1)
      .gutterIconRenderer(new GutterIconRenderer() {
        @Override
        public boolean equals(Object obj) {
          return false;
        }

        @Override
        public int hashCode() {
          return 0;
        }

        @Override
        public @NotNull Icon getIcon() {
          return EmptyIcon.ICON_0;
        }
      })
    .createUnconditionally().copy(false);
    builder.description("X");
    builder.descriptionAndTooltip("X");
    builder.endOfLine();
    builder.escapedToolTip("X");
    builder.fileLevelAnnotation();
    builder.group(444);

    builder.gutterIconRenderer(new GutterIconRenderer() {
      @Override
      public boolean equals(Object obj) {
        return false;
      }

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public @NotNull Icon getIcon() {
        return EmptyIcon.ICON_0;
      }
    });
    builder.navigationShift(3);
    builder.needsUpdateOnTyping(true);
    builder.problemGroup(new ProblemGroup() {
      @Override
      public String getProblemName() {
        return "";
      }
    });
    builder.range(1,2).range(1,2);
    builder.severity(HighlightSeverity.ERROR);
    builder.textAttributes(new TextAttributes());
    builder.textAttributes(CodeInsightColors.LINE_NONE_COVERAGE);
    builder.unescapedToolTip("X");
    builder.createUnconditionally();
  }
  private static void checkThrowsWhenCalledTwiceOrAfterCreate(Consumer<? super HighlightInfo.Builder> method) {
    // twice is bad
    UsefulTestCase.assertThrows(IllegalArgumentException.class, ()->{
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR);
      method.accept(builder);
      method.accept(builder);
    });
    // once is OK
    {
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(1, 2);
      method.accept(builder);
      builder.createUnconditionally();
    }
    // after .create() is bad
    UsefulTestCase.assertThrows(IllegalArgumentException.class, ()->{
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(2,4);
      builder.createUnconditionally();
      method.accept(builder);
    });
  }
}
