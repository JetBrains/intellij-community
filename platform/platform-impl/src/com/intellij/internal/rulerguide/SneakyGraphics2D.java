// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.rulerguide;

import com.intellij.ui.Graphics2DDelegate;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Point2D;
import java.text.AttributedCharacterIterator;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

import static java.lang.Math.max;
import static java.lang.Math.min;

final class SneakyGraphics2D extends Graphics2DDelegate {

    private final Collection<ComponentBounds> poi;

    SneakyGraphics2D(Graphics2D g2d) {
        super(g2d);
        this.poi = new LinkedHashSet<>();
    }

    private SneakyGraphics2D(Graphics2D g2d, Collection<ComponentBounds> poi) {
        super(g2d);
        this.poi = poi;
    }

    public Collection<ComponentBounds> getPOI() {
        return Collections.unmodifiableCollection(poi);
    }

    @Override
    public void drawChars(char[] data, int offset, int length, int x, int y) {
        super.drawChars(data, offset, length, x, y);
        if (data.length > 0) updateBaselines(x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        super.drawString(iterator, x, y);
        updateBaselines(x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        super.drawString(iterator, x, y);
        updateBaselines(x, y);
    }

    @Override
    public void drawString(String s, float x, float y) {
        super.drawString(s, x, y);
        if (!s.isEmpty()) updateBaselines(x, y);
    }

    @Override
    public void drawString(String str, int x, int y) {
        super.drawString(str, x, y);
        if (!str.isEmpty()) updateBaselines(x, y);
    }

    private void updateBaselines(float x, float y) {
        Point text = (Point) getTransform().transform(new Point2D.Float(x, y), new Point());

        Rectangle cb = getClipBounds();
        Point start = (Point) getTransform().transform(new Point(cb.x, cb.y), new Point());
        Point end = (Point) getTransform().transform(new Point(cb.x + cb.width, cb.y + cb.height), new Point());
        
        int minX = min(start.x, end.x);
        int minY = min(start.y, end.y);
        int maxX = max(start.x, end.x);
        int maxY = max(start.y, end.y);
        ComponentBounds bounds = new ComponentBounds(
                minX, minY,
                maxX - minX, maxY - minY,
                -1, -1
        );
        bounds.horizontalBaseline = text.y - bounds.y;
        bounds.verticalBaseline = text.x - bounds.x;
        poi.add(bounds);
    }

    @Override
    public @NotNull Graphics create() {
        return new SneakyGraphics2D((Graphics2D) super.create(), poi);
    }
}
