package org.jetbrains.intellij.plugins.journey.diagram.ui;

import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorWrapper;

import javax.swing.border.LineBorder;
import java.awt.*;

public class JourneyLineBorder extends LineBorder {
  private final JourneyEditorWrapper editorWrapper;
  private final Graph2DView view;

  public JourneyLineBorder(Color color, int thickness, JourneyEditorWrapper editorWrapper, Graph2DView view) {
    super(color, thickness);
    this.editorWrapper = editorWrapper;
    this.view = view;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Rectangle bounds = editorWrapper.getDrawableRect(view);
    if (editorWrapper.isRemoved() || bounds.x + bounds.width < 0 || bounds.y + bounds.height < 0 ||
        bounds.x > view.getViewSize().width || bounds.y > view.getViewSize().height) {
      editorWrapper.getEditorComponent().setVisible(false);
      view.getCanvasComponent().revalidate();
    }
    super.paintBorder(c, g, x, y, width, height);
  }
}
