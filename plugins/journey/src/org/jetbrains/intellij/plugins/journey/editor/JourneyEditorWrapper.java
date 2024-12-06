package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNode;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyLineBorder;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyTitleBar;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class JourneyEditorWrapper extends JPanel {
  private NodeRealizer realizer;
  private final Editor editor;
  private final JourneyTitleBar titleBar;

  public JourneyEditorWrapper(Editor editor, JourneyNode node, NodeRealizer realizer, SmartPsiElementPointer psiMember, Graph2DView view) {
    super(new BorderLayout());
    this.editor = editor;
    this.realizer = realizer;
    setVisible(false);
    setPreferredSize(new Dimension(editor.getComponent().getWidth(), editor.getComponent().getHeight()));
    titleBar = new JourneyTitleBar(editor, node);
    setTitle(psiMember);
    editor.getComponent().add(titleBar, BorderLayout.NORTH);
    Border border = new JourneyLineBorder(JBColor.LIGHT_GRAY, 1, this, view);
    editor.getComponent().setBorder(border);
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

  // TODO hack! Replace it
  public boolean isRemoved() {
    return realizer.getNode().toString().equals("node without a graph");
  }

  public void updateRealizer(NodeRealizer realizer) {
    this.realizer = realizer;
  }

  public JComponent getEditorComponent() {
    return editor.getComponent();
  }

  public Editor getEditor() {
    return editor;
  }

  public void setTitle(SmartPsiElementPointer psiMember) {
    titleBar.setTitle(ObjectUtils.notNull(PsiUtil.tryGetPresentableTitle(psiMember != null ? psiMember.getElement() : null), () -> ""));
  }
}