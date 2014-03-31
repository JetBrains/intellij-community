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

import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;

import java.awt.*;

public class Sprite {
    private final char character;
    private final int fontType;
    private final Color color;
    public final Point position; // mutable
    public int dx; // mutable
    public int dy; // mutable

    Sprite(char character, int fontType, Color color, Point position, int dx, int dy) {
        this.character = character;
        this.fontType = fontType;
        this.color = color;
        this.position = position;
        this.dx = dx;
        this.dy = dy;
    }

    public void paint(EditorImpl editor, Graphics g) {
        g.setFont(EditorUtil.fontForChar(character, fontType, editor).getFont());
        g.setColor(color);
        g.drawChars(new char[]{character}, 0, 1, position.x, position.y + editor.getAscent());
    }

    public void requestRepaintInEditor(EditorImpl editor) {
        int sizeEstimate = editor.getLineHeight() * 4;
        int yStart = Math.min(position.y - sizeEstimate / 2, editor.getLineHeight() * (editor.offsetToVisualPosition(editor.getDocument().getTextLength()).line - 1));
        int yEnd = position.y + sizeEstimate / 2;
        editor.getContentComponent().repaint(position.x - sizeEstimate / 2,
                yStart,
                sizeEstimate,
                yEnd - yStart);
    }
}
