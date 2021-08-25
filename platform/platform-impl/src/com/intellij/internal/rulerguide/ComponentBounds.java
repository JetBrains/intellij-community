package com.intellij.internal.rulerguide;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

class ComponentBounds extends Rectangle {

    public int horizontalBaseline;
    public int verticalBaseline;

    ComponentBounds(int x, int y, int width, int height, int horizontalBaseline, int verticalBaseline) {
        super(x, y, width, height);
        this.horizontalBaseline = horizontalBaseline;
        this.verticalBaseline = verticalBaseline;
    }

    @SuppressWarnings("unused")
    public void setBaselines(int horizontalBaseline, int verticalBaseline) {
        this.horizontalBaseline = horizontalBaseline;
        this.verticalBaseline = verticalBaseline;
    }

    @SuppressWarnings("unused")
    @NotNull
    public Point getBaselineLocation() {
        int xx = x + verticalBaseline;
        int yy = y + horizontalBaseline;
        return new Point(xx, yy);
    }

  @Override
    public String toString() {
        return "ComponentBounds{" +
                "x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                ", horizontalBaseline=" + horizontalBaseline +
                ", verticalBaseline=" + verticalBaseline +
                '}';
    }
}
