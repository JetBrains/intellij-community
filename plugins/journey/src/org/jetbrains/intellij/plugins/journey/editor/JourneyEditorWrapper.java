package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.psi.PsiMember;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNode;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyLineBorder;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyTitleBar;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;

import static com.intellij.ide.actions.QualifiedNameProviderUtil.getQualifiedName;

public class JourneyEditorWrapper extends JPanel {
  private NodeRealizer realizer;
  public final Editor editor;

  public JourneyEditorWrapper(Editor editor, JourneyNode node, NodeRealizer realizer, PsiMember psiMember, Graph2DView view) {
    super(new BorderLayout());
    this.editor = editor;
    this.realizer = realizer;
    setVisible(false);
    setPreferredSize(new Dimension(editor.getComponent().getWidth(), editor.getComponent().getHeight()));

    Runnable runnable1 = () -> {
      node.setFullViewState(false);
    };
    Runnable runnable2 = () -> {
      node.setFullViewState(true);
    };

    editor.getComponent().add(new JourneyTitleBar(ObjectUtils.notNull(getQualifiedName(psiMember), () -> "No title"), editor, List.of(runnable1, runnable2)), BorderLayout.NORTH);
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

  public void updateRealizer(NodeRealizer realizer) {
    this.realizer = realizer;
  }

  public void setVisibleEditor(boolean visible) {
    editor.getComponent().setVisible(visible);
  }

  public JComponent getEditorComponent() {
    return editor.getComponent();
  }

  public NodeRealizer getRealizer() {
    return realizer;
  }
}