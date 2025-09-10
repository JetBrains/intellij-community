// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


/**
 * IDEA: Editor horizontal text alignment
 */
interface XCorrector {
  float startX(int line);
  int lineWidth(int line, float x);
  int emptyTextX();
  int minX(int startLine, int endLine);
  int maxX(int startLine, int endLine);
  int lineSeparatorStart(int minX);
  int lineSeparatorEnd(int maxX);
  float singleLineBorderStart(float x);
  float singleLineBorderEnd(float x);
  int marginX(float marginWidth);
  List<Integer> softMarginsX();

  static @NotNull XCorrector create(@NotNull EditorView view, @NotNull Insets insets) {
    return view.getEditor().isRightAligned() ? new RightAligned(view) : new LeftAligned(view, insets);
  }

  final class LeftAligned implements XCorrector {
    private final EditorView myView;
    private final int myLeftInset;

    private LeftAligned(@NotNull EditorView view, @NotNull Insets insets) {
      myView = view;
      myLeftInset = insets.left;
    }

    @Override
    public float startX(int line) {
      return myLeftInset;
    }

    @Override
    public int emptyTextX() {
      return myLeftInset;
    }

    @Override
    public int minX(int startLine, int endLine) {
      return myLeftInset;
    }

    @Override
    public int maxX(int startLine, int endLine) {
      return minX(startLine, endLine) + myView.getMaxTextWidthInLineRange(startLine, endLine - 1) - 1;
    }

    @Override
    public float singleLineBorderStart(float x) {
      return x;
    }

    @Override
    public float singleLineBorderEnd(float x) {
      return x + 1;
    }

    @Override
    public int lineWidth(int line, float x) {
      return (int)x - myLeftInset;
    }

    @Override
    public int lineSeparatorStart(int maxX) {
      return myLeftInset;
    }

    @Override
    public int lineSeparatorEnd(int maxX) {
      return EditorPainter.isMarginShown(myView.getEditor()) ? Math.min(marginX(EditorPainter.getBaseMarginWidth(myView)), maxX) : maxX;
    }

    @Override
    public int marginX(float marginWidth) {
      return (int)(myLeftInset + marginWidth);
    }

    /**
     * Visual indent guides (soft margins) initial implementation (IDEA-99875)
     */
    @Override
    public List<Integer> softMarginsX() {
      List<Integer> margins = myView.getEditor().getSettings().getSoftMargins();
      List<Integer> result = new ArrayList<>(margins.size());
      for (Integer margin : margins) {
        result.add((int)(myLeftInset + margin * myView.getPlainSpaceWidth()));
      }
      return result;
    }
  }

  final class RightAligned implements XCorrector {
    private final EditorView myView;

    private RightAligned(@NotNull EditorView view) {
      myView = view;
    }

    @Override
    public float startX(int line) {
      return myView.getRightAlignmentLineStartX(line);
    }

    @Override
    public int lineWidth(int line, float x) {
      return (int)(x - myView.getRightAlignmentLineStartX(line));
    }

    @Override
    public int emptyTextX() {
      return myView.getRightAlignmentMarginX();
    }

    @Override
    public int minX(int startLine, int endLine) {
      return myView.getRightAlignmentMarginX() - myView.getMaxTextWidthInLineRange(startLine, endLine - 1) - 1;
    }

    @Override
    public int maxX(int startLine, int endLine) {
      return myView.getRightAlignmentMarginX() - 1;
    }

    @Override
    public float singleLineBorderStart(float x) {
      return x - 1;
    }

    @Override
    public float singleLineBorderEnd(float x) {
      return x;
    }

    @Override
    public int lineSeparatorStart(int minX) {
      return EditorPainter.isMarginShown(myView.getEditor()) ? Math.max(marginX(EditorPainter.getBaseMarginWidth(myView)), minX) : minX;
    }

    @Override
    public int lineSeparatorEnd(int maxX) {
      return maxX;
    }

    @Override
    public int marginX(float marginWidth) {
      return (int)(myView.getRightAlignmentMarginX() - marginWidth);
    }

    /**
     * Visual indent guides (soft margins) initial implementation (IDEA-99875)
     */
    @Override
    public List<Integer> softMarginsX() {
      List<Integer> margins = myView.getEditor().getSettings().getSoftMargins();
      List<Integer> result = new ArrayList<>(margins.size());
      for (Integer margin : margins) {
        result.add((int)(myView.getRightAlignmentMarginX() - margin * myView.getPlainSpaceWidth()));
      }
      return result;
    }
  }
}
