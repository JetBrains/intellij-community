package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.ui.JBColor;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyLineBorder;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyTitleBar;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class JourneyEditorWrapper extends JPanel {
  private final JComponent editorComponent;
  private NodeRealizer realizer;

  public JourneyEditorWrapper(Editor editor, NodeRealizer realizer, String title, Graph2DView view) {
    super(new BorderLayout());
    editorComponent = editor.getComponent();
    this.realizer = realizer;
    setVisible(false);
    setPreferredSize(new Dimension(editor.getComponent().getWidth(), editor.getComponent().getHeight()));

    editorComponent.add(new JourneyTitleBar(title, editorComponent), BorderLayout.NORTH);
    Border border = new JourneyLineBorder(JBColor.DARK_GRAY, 1, this, view);
    editorComponent.setBorder(border);
  }

  public Rectangle getDrawableRect(Graph2DView view) {
    NodeRealizer nodeRealizer = realizer;
    double zoom = view.getZoom();
    double x = (nodeRealizer.getX() - view.getViewPoint().x) * zoom;
    double y = (nodeRealizer.getY() - view.getViewPoint().y) * zoom;
    double width = nodeRealizer.getWidth() * zoom;
    double height = nodeRealizer.getHeight() * zoom;
    return new Rectangle((int)(x), (int)(y), (int)(width), (int)(height));
  }

  public void updateRealizer(NodeRealizer realizer) {
    this.realizer = realizer;
  }

  public void setVisibleEditor(boolean visible) {
    editorComponent.setVisible(visible);
  }

  public JComponent getEditorComponent() {
    return editorComponent;
  }
}