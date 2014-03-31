/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.breakout;

import com.intellij.openapi.editor.Editor;
import com.intellij.util.text.CharArrayUtil;

import java.awt.*;
import java.util.ArrayList;

public class BounceDetector {
    private final Editor editor;

    public BounceDetector(Editor editor) {
        this.editor = editor;
    }

    public java.util.List<Integer> moveSprite(Sprite sprite) {
        java.util.List<VisualPosition> positionsToClear = new ArrayList<VisualPosition>();
        int dxRemaining = sprite.dx;
        int dyRemaining = sprite.dy;
        while (dxRemaining != 0 || dyRemaining != 0) {
            Point p = sprite.position;
            int leftWall = getNearestWallAtLeft(p.x, p.y);
            int rightWall = getNearestWallAtRight(p.x, p.y);
            int aboveWall = getNearestWallAbove(p.x, p.y);
            int belowWall = getNearestWallBelow(p.x, p.y);
            int dxCurrent = dxRemaining;
            int dyCurrent = dyRemaining;
            int maxXStep = getWidthEstimate() / 2;
            if (Math.abs(dxCurrent) > maxXStep) {
                dxCurrent = (dxCurrent > 0) ? maxXStep : -maxXStep;
                dyCurrent = dyCurrent * dxCurrent / dxRemaining;
            }
            int maxYStep = getHeightEstimate() / 2;
            if (Math.abs(dyCurrent) > maxYStep) {
                int dyCurrentOld = dyCurrent;
                dyCurrent = (dyCurrent > 0) ? maxYStep : -maxYStep;
                dxCurrent = dxCurrent * dyCurrent / dyCurrentOld;
            }
            sprite.position.translate(dxCurrent, dyCurrent);
            dxRemaining -= dxCurrent;
            dyRemaining -= dyCurrent;
            VisualPosition pos = getVisualPosition(editor, sprite);
            if (sprite.position.x <= leftWall) {
                sprite.position.x = 2 * leftWall - sprite.position.x;
                sprite.dx = -sprite.dx;
                dxRemaining = -dxRemaining;
                int columnAdjustment = sprite.position.x == leftWall ? -1 : 0;
                positionsToClear.add(new VisualPosition(pos.line, pos.column + columnAdjustment));
                positionsToClear.add(new VisualPosition(pos.line + 1, pos.column + columnAdjustment));
            }
            if (sprite.position.y <= aboveWall) {
                sprite.position.y = 2 * aboveWall - sprite.position.y;
                sprite.dy = -sprite.dy;
                dyRemaining = -dyRemaining;
                int lineAdjustment = sprite.position.y == aboveWall ? -1 : 0;
                positionsToClear.add(new VisualPosition(pos.line + lineAdjustment, pos.column));
                positionsToClear.add(new VisualPosition(pos.line + lineAdjustment, pos.column + 1));
            }
            if (sprite.position.x >= rightWall) {
                sprite.position.x = 2 * rightWall - sprite.position.x;
                sprite.dx = -sprite.dx;
                dxRemaining = -dxRemaining;
                positionsToClear.add(new VisualPosition(pos.line, pos.column + 1));
                positionsToClear.add(new VisualPosition(pos.line + 1, pos.column + 1));
            }
            if (sprite.position.y >= belowWall) {
                sprite.position.y = 2 * belowWall - sprite.position.y;
                sprite.dy = -sprite.dy;
                dyRemaining = -dyRemaining;
                positionsToClear.add(new VisualPosition(pos.line + 1, pos.column));
                positionsToClear.add(new VisualPosition(pos.line + 1, pos.column + 1));
            }
        }
        java.util.List<Integer> offsetsToClear = new ArrayList<Integer>();
        for (VisualPosition visualPosition : positionsToClear) {
            if (visualPosition.line < 0 || visualPosition.column < 0) {
                continue;
            }
            int offset = editor.logicalPositionToOffset(editor.visualToLogicalPosition(new com.intellij.openapi.editor.VisualPosition(visualPosition.line, visualPosition.column)));
            if (offset < 0 || offset >= editor.getDocument().getTextLength()) {
                continue;
            }
            if (editor.getDocument().getCharsSequence().charAt(offset) != '\n') {
                offsetsToClear.add(offset);
            }
        }
        return offsetsToClear;
    }

    private int getLineNumber(int y) {
        return y / editor.getLineHeight();
    }

    public int getLineStart(int line) {
        if (line < 0) {
            return Integer.MAX_VALUE;
        }
        int lineStartOffset = editor.logicalPositionToOffset(editor.visualToLogicalPosition(new com.intellij.openapi.editor.VisualPosition(line, 0)));
        CharSequence charSequence = editor.getDocument().getCharsSequence();
        int nonWsOffset = CharArrayUtil.shiftForward(charSequence, lineStartOffset, " \t");
        if (nonWsOffset >= charSequence.length() || charSequence.charAt(nonWsOffset) == '\n') {
            return Integer.MAX_VALUE;
        }
        else {
            return editor.logicalPositionToXY(editor.offsetToLogicalPosition(nonWsOffset)).x - getWidthEstimate();
        }
    }

    public int getLineEnd(int line) {
        if (line < 0) {
            return Integer.MAX_VALUE;
        }
        int lineEndOffset = editor.logicalPositionToOffset(editor.visualToLogicalPosition(new com.intellij.openapi.editor.VisualPosition(line, 2000)));
        return editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineEndOffset)).x;
    }

    private int getWidthEstimate() {
        return editor.getLineHeight();
    }

    private int getHeightEstimate() {
        return editor.getLineHeight();
    }

    public int getNearestWallAtRight(int x, int y) {
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        int wallX = visibleArea.x + visibleArea.width - getWidthEstimate();
        int currLine = getLineNumber(y);
        int lineStart = getLineStart(currLine);
        int nextLineStart = getLineStart(currLine + 1);
        if (lineStart > x && lineStart < wallX) {
            wallX = lineStart;
        }
        if (nextLineStart > x && nextLineStart < wallX) {
            wallX = nextLineStart;
        }
        return wallX;
    }

    public int getNearestWallAtLeft(int x, int y) {
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        int wallX = visibleArea.x;
        int currLine = getLineNumber(y);
        int lineEnd = getLineEnd(currLine);
        int nextLineEnd = getLineEnd(currLine + 1);
        if (lineEnd < x && lineEnd > wallX) {
            wallX = lineEnd;
        }
        if (nextLineEnd < x && nextLineEnd > wallX) {
            wallX = nextLineEnd;
        }
        return wallX;
    }

    public int getNearestWallAbove(int x, int y) {
        int currLine = getLineNumber(y);
        int lineStart = getLineStart(currLine);
        int lineEnd = getLineEnd(currLine);
        if (x > lineEnd || x < lineStart) {
            int prevLineStart = getLineStart(currLine - 1);
            int prevLineEnd = getLineEnd(currLine - 1);
            if (x > prevLineStart && x < prevLineEnd) {
                return currLine * editor.getLineHeight();
            }
        }
        return editor.getScrollingModel().getVisibleArea().y;
    }

    public int getNearestWallBelow(int x, int y) {
        int currLine = getLineNumber(y);
        int lineStart = getLineStart(currLine + 1);
        int lineEnd = getLineEnd(currLine + 1);
        if (x > lineEnd || x < lineStart) {
            int nextLineStart = getLineStart(currLine + 2);
            int nextLineEnd = getLineEnd(currLine + 2);
            if (x > nextLineStart && x < nextLineEnd) {
                return (currLine + 2) * editor.getLineHeight() - getHeightEstimate();
            }
        }
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        return visibleArea.y + visibleArea.height - getHeightEstimate();
    }

    private VisualPosition getVisualPosition(Editor editor, Sprite sprite) {
        Point p = new Point(sprite.position);
        if (p.x < 0 && p.y < 0) {
            return new VisualPosition(-1, -1);
        }
        else if (p.x < 0) {
            p.x = 0;
            com.intellij.openapi.editor.VisualPosition pos = getCharacterVisualPosition(editor, p);
            return new VisualPosition(pos.line, -1);
        }
        else if (p.y < 0) {
            p.y = 0;
            com.intellij.openapi.editor.VisualPosition pos = getCharacterVisualPosition(editor, p);
            return new VisualPosition(-1, pos.column);
        }
        else {
            com.intellij.openapi.editor.VisualPosition pos = getCharacterVisualPosition(editor, p);
            return new VisualPosition(pos.line, pos.column);
        }
    }

    private com.intellij.openapi.editor.VisualPosition getCharacterVisualPosition(Editor editor, Point p) {
        com.intellij.openapi.editor.VisualPosition pos = editor.xyToVisualPosition(p);
        if (editor.visualPositionToXY(pos).x > p.x) {
            return new com.intellij.openapi.editor.VisualPosition(pos.line, pos.column - 1);
        }
        else {
            return pos;
        }
    }

    private static class VisualPosition {
        public final int line;
        public final int column;

        private VisualPosition(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }
}
