package com.intellij.android.designer.designSurface.layout.grid;

import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class GridSelectionDecorator extends ResizeSelectionDecorator {
  public GridSelectionDecorator(Color color, int lineWidth) {
    super(color, lineWidth);
  }

  @Override
  protected Rectangle getBounds(DecorationLayer layer, RadComponent component) {
    return getCellBounds(layer, component);
  }

  public abstract Rectangle getCellBounds(Component layer, RadComponent component);

  public static Rectangle calculateBounds(Component layer,
                                          GridInfo gridInfo,
                                          RadComponent parent,
                                          RadComponent component,
                                          int row,
                                          int column,
                                          int rowSpan,
                                          int columnSpan) {
    Rectangle bounds = parent.getBounds(layer);

    bounds.x += gridInfo.vLines[column];
    bounds.width = gridInfo.vLines[column + columnSpan] - gridInfo.vLines[column];

    bounds.y += gridInfo.hLines[row];
    bounds.height = gridInfo.hLines[row + rowSpan] - gridInfo.hLines[row];

    Rectangle componentBounds = component.getBounds(layer);
    if (!bounds.contains(componentBounds.x, componentBounds.y)) {
      return componentBounds;
    }

    return bounds;
  }
}