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

import com.intellij.ide.actions.UndoAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EscapeAction;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class EventListener implements EditorMouseListener, EditorMouseMotionListener, AnActionListener {
    private static final Key<RangeHighlighter> HIGHLIGHTER_KEY = Key.create("EditorBreakoutMode.highlighter");

    private Point lastLocation;
    private Sprite currentChar;
    private int dx;
    private int dy;

    @Override
    public void mousePressed(EditorMouseEvent e) {
        Editor editor = e.getEditor();
        if (!BreakoutMode.getInstance().isEnabled(editor)) {
            return;
        }
        lastLocation = e.getMouseEvent().getPoint();
        currentChar = null;
        dx = dy = 0;
    }

    @Override
    public void mouseDragged(EditorMouseEvent e) {
        Editor editor = e.getEditor();
        if (!BreakoutMode.getInstance().isEnabled(editor)) {
            return;
        }
        EditorImpl editorImpl = (EditorImpl) editor;
        editor.getCaretModel().removeSecondaryCarets();
        editor.getSelectionModel().removeSelection();
        Point currentPoint = e.getMouseEvent().getPoint();
        if (currentChar == null) {
            if (lastLocation == null) {
                return;
            }
            Sprite sprite = createSpriteFor(editorImpl, lastLocation);
            if (sprite == null) {
                return;
            }
            editorImpl.setCaretEnabled(false);
            RangeHighlighter highlighter = editor.getUserData(HIGHLIGHTER_KEY);
            if (highlighter == null) {
                highlighter = createRenderingHighlighter(editorImpl);
                editor.putUserData(HIGHLIGHTER_KEY, highlighter);
            }
            currentChar = sprite;
        }
        else {
            if (lastLocation != null) {
                int newDx = currentPoint.x - lastLocation.x;
                int newDy = currentPoint.y - lastLocation.y;
                currentChar.position.translate(newDx, newDy);
                currentChar.requestRepaintInEditor(editorImpl);
                if (newDx * newDx + newDy * newDy < 10) {
                    dx += newDx;
                    dy += newDy;
                }
                else {
                    dx = newDx;
                    dy = newDy;
                }
            }
        }
        lastLocation = currentPoint;
    }

    @Override
    public void mouseReleased(EditorMouseEvent e) {
        Editor editor = e.getEditor();
        if (!BreakoutMode.getInstance().isEnabled(editor)) {
            return;
        }
        EditorImpl editorImpl = (EditorImpl) editor;
        editorImpl.setCaretEnabled(true);
        if (currentChar != null) {
            currentChar.dx = dx;
            currentChar.dy = dy;
        }
    }

    @Override
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (action instanceof UndoAction || action instanceof EscapeAction) {
            EditorImpl editor = getEditor(dataContext);
            if (BreakoutMode.getInstance().isEnabled(editor)) {
                getAnimationManager().stopAnimation(editor);
                editor.putUserData(HIGHLIGHTER_KEY, null);
            }
        }
    }

    private Sprite createSpriteFor(EditorImpl editor, Point point) {
        final int offset = offsetForPoint(editor, point);
        final Document document = editor.getDocument();
        if (offset < 0 || offset >= document.getTextLength()) {
            return null;
        }
        char c = document.getCharsSequence().charAt(offset);
        if (Character.isWhitespace(c)) {
            return null;
        }
        IterationState state = new IterationState(editor, offset, offset + 1, false);
        Sprite sprite;
        try {
            TextAttributes attributes = state.getMergedAttributes();
            int fontType = attributes.getFontType();
            Color color = attributes.getForegroundColor();
            sprite = getAnimationManager().addSprite(editor, c, fontType, color, editor.visualPositionToXY(editor.offsetToVisualPosition(offset)), 0, 0);
        }
        finally {
            state.dispose();
        }
        DocumentModifier.removeCharacter(editor, offset);
        return sprite;
    }

    private int offsetForPoint(Editor editor, Point p) {
        int offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(p));
        if (editor.visualPositionToXY(editor.offsetToVisualPosition(offset)).x > p.x) {
            offset--;
        }
        return offset;
    }

    private RangeHighlighter createRenderingHighlighter(EditorImpl editor) {
        RangeHighlighter highlighter;
        highlighter = editor.getMarkupModel().addRangeHighlighter(0,
                editor.getDocument().getTextLength(), Integer.MIN_VALUE, null,
                HighlighterTargetArea.EXACT_RANGE);
        highlighter.setGreedyToLeft(true);
        highlighter.setGreedyToRight(true);
        highlighter.setCustomRenderer(new CustomHighlighterRenderer() {
            @Override
            public void paint(@NotNull Editor editor, @NotNull RangeHighlighter rangeHighlighter, @NotNull Graphics g) {
                getAnimationManager().paint((EditorImpl) editor, g);
            }
        });
        return highlighter;
    }

    private static EditorImpl getEditor(DataContext context) {
        Editor editor = PlatformDataKeys.EDITOR.getData(context);
        return editor instanceof EditorImpl ? (EditorImpl) editor : null;
    }

    private static AnimationManager getAnimationManager() {
        return BreakoutMode.getInstance().getAnimationManager();
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {

    }

    @Override
    public void mouseEntered(EditorMouseEvent e) {

    }

    @Override
    public void mouseExited(EditorMouseEvent e) {

    }

    @Override
    public void mouseMoved(EditorMouseEvent e) {

    }

    @Override
    public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {

    }

    @Override
    public void beforeEditorTyping(char c, DataContext dataContext) {

    }
}
