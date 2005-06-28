package org.intellij.images.options;

/**
 * Images editor options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface EditorOptions extends Cloneable {
    GridOptions getGridOptions();

    TransparencyChessboardOptions getTransparencyChessboardOptions();

    ZoomOptions getZoomOptions();

    void inject(EditorOptions options);

    boolean setOption(String name, Object value);
}
