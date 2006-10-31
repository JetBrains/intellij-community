package org.intellij.images.ui;

/**
 * Image Component manager. It can toggle backround transparency, grid, etc.
 *
 * @author Alexey Efimov
 */
public interface ImageComponentDecorator {
    void setTransparencyChessboardVisible(boolean visible);

    boolean isTransparencyChessboardVisible();

    /**
     * Return <code>true</code> if this decorator is enabled for this action place.
     *
     * @param place Action place
     * @return <code>true</code> is decorator is enabled
     */
    boolean isEnabledForActionPlace(String place);
}
