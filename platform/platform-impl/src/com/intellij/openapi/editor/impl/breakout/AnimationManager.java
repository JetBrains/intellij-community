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

import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AnimationManager {
    private static final long ANIMATION_INTERVAL = 50;
    private static final int SPEED_SCALE = 2;

    private final Map<EditorImpl, List<Sprite>> state = new HashMap<EditorImpl, List<Sprite>>();
    private final Alarm alarm = new Alarm();

    public Sprite addSprite(EditorImpl editor, char c, int fontType, Color color, Point from, int dx, int dy) {
        List<Sprite> chars = state.get(editor);
        if (chars == null) {
            chars = new ArrayList<Sprite>();
            state.put(editor, chars);
        }
        Sprite sprite = new Sprite(c, fontType, color, from, dx * SPEED_SCALE, dy * SPEED_SCALE);
        chars.add(sprite);
        sprite.requestRepaintInEditor(editor);
        startAnimation();
        return sprite;
    }

    private void startAnimation() {
        if (alarm.getActiveRequestCount() == 0) {
            alarm.addRequest(new Runnable() {
                @Override
                public void run() {
                    Iterator<Map.Entry<EditorImpl, List<Sprite>>> iterator = state.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<EditorImpl, List<Sprite>> entry = iterator.next();
                        EditorImpl editor = entry.getKey();
                        if (editor.isDisposed()) {
                            iterator.remove();
                        }
                        else {
                            BounceDetector bounceDetector = new BounceDetector(editor);
                            for (Sprite sprite : entry.getValue()) {
                                sprite.requestRepaintInEditor(editor);
                                List<Integer> offsetsToClear = bounceDetector.moveSprite(sprite);
                                sprite.requestRepaintInEditor(editor);
                                Collections.sort(offsetsToClear);
                                for (int i = offsetsToClear.size() - 1; i >= 0 ; i--) {
                                    DocumentModifier.removeCharacter(editor, offsetsToClear.get(i));
                                }
                            }
                        }
                    }
                    if (!state.isEmpty()) {
                        startAnimation();
                    }
                }
            }, ANIMATION_INTERVAL);
        }
    }

    public void paint(EditorImpl editor, Graphics g) {
        List<Sprite> sprites = state.get(editor);
        if (sprites == null) {
            return;
        }
        for (Sprite sprite : sprites) {
            sprite.paint(editor, g);
//            debugWallsPainting(editor, g, sprite);
        }
//        debugBorderPainting(editor, g);
    }

    private void debugWallsPainting(EditorImpl editor, Graphics g, Sprite sprite) {
        BounceDetector detector = new BounceDetector(editor);
        Point p = sprite.position;
        int nearestWallAbove = detector.getNearestWallAbove(p.x, p.y);
        int nearestWallBelow = detector.getNearestWallBelow(p.x, p.y);
        int nearestWallLeft = detector.getNearestWallAtLeft(p.x, p.y);
        int nearestWallRight = detector.getNearestWallAtRight(p.x, p.y);
        g.setColor(JBColor.CYAN);
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        g.drawLine(visibleArea.x, nearestWallAbove, visibleArea.x + visibleArea.width, nearestWallAbove);
        g.drawLine(visibleArea.x, nearestWallBelow, visibleArea.x + visibleArea.width, nearestWallBelow);
        g.drawLine(nearestWallLeft, visibleArea.y, nearestWallLeft, visibleArea.y + visibleArea.height);
        g.drawLine(nearestWallRight, visibleArea.y, nearestWallRight, visibleArea.y + visibleArea.height);
        g.fillRect(p.x, p.y, 1, 1);
    }

    private void debugBorderPainting(EditorImpl editor, Graphics g) {
        int lineCount = editor.getDocument().getLineCount();
        int lineHeight = editor.getLineHeight();
        BounceDetector detector = new BounceDetector(editor);
        g.setColor(JBColor.MAGENTA);
        for (int i = 0; i < lineCount; i++) {
            int start = detector.getLineStart(i);
            int end = detector.getLineEnd(i);
            g.drawLine(start, i * lineHeight, start, (i + 1) * lineHeight);
            g.drawLine(end, i * lineHeight, end, (i + 1) * lineHeight);
        }
    }

    public void stopAnimation(EditorImpl editor) {
        List<Sprite> sprites = state.remove(editor);
        if (sprites == null) {
            return;
        }
        for (Sprite sprite : sprites) {
            sprite.requestRepaintInEditor(editor);
        }
        if (state.isEmpty()) {
            alarm.cancelAllRequests();
        }
    }
}
