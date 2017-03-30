package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffFilesTest;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import junit.framework.Test;
import junit.framework.TestSuite;

public class MergeActionsTest extends TestSuite {
  public MergeActionsTest() {
    addTestFile("_merge1");
    addTestFile("_merge2");
  }

  private void addTestFile(String fileName) {
    addTest(new MyTestCase(fileName) {});
  }

  public static Test suite() {
    return new MergeActionsTest();
  }

  public static abstract class MyTestCase extends DiffFilesTest.MyIdeaTestCase {
    protected MyTestCase(String name) {
      super(name, ComparisonPolicy.DEFAULT);
    }

    @Override
    protected void setContents(DiffPanelImpl diffPanel, String content1, String content2) {
      SimpleContent diffContent1 = new SimpleContent(content1);
      diffContent1.setReadOnly(false);
      diffPanel.setContents(diffContent1, new SimpleContent(content2));
    }

    @Override
    protected String process(Editor editor) {
      StringBuffer buffer = new StringBuffer();
      RangeHighlighter[] allHighlighters = editor.getMarkupModel().getAllHighlighters();
      for (int i = 0; i < allHighlighters.length; i++) {
        RangeHighlighter highlighter = allHighlighters[i];
        GutterMark iconRenderer = highlighter.getGutterIconRenderer();
        if (iconRenderer != null) {
          buffer.append(iconRenderer.getTooltipText());
          buffer.append(' ');
          buffer.append(highlighter.getStartOffset());
          buffer.append('-');
          buffer.append(highlighter.getEndOffset());
          buffer.append('\n');
        }
      }
      return buffer.toString();
    }
  }
}
