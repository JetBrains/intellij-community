package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;

import javax.swing.*;

class HackyBoundsTranslationNodeComponent extends JPanel {
  private Graph2DView view;
  private NodeRealizer realizer;

  HackyBoundsTranslationNodeComponent(Graph2DView view, NodeRealizer realizer) {
    this.setView(view);
    this.setRealizer(realizer);
  }

  void setRealizer(NodeRealizer realizer) {
    this.realizer = realizer;
  }

  void setView(Graph2DView view) {
    this.view = view;
  }

  @Override
  public void setBounds(int x__, int y__, int width, int height) {
    final var x = getRealizer().getX();
    final var y = getRealizer().getY();
    int compY = ((int)y) - getView().getViewPoint().y;
    int compX = ((int)x) - getView().getViewPoint().x;
    double zoom = getView().getZoom();
    int x1 = (int)(compX * zoom);
    int y1 = (int)((compY) * zoom);
    super.setBounds(x1, y1, ((int)(width * zoom)), (int)(height * zoom));
  }

  public Graph2DView getView() {
    return view;
  }

  public NodeRealizer getRealizer() {
    return realizer;
  }
}
