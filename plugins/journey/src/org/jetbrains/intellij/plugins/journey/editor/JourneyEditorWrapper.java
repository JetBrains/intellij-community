package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyLineBorder;
import org.jetbrains.intellij.plugins.journey.diagram.ui.JourneyTitleBar;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.ide.actions.QualifiedNameProviderUtil.getQualifiedName;
import static org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager.foldOnlyRange;

public class JourneyEditorWrapper extends JPanel {
  private final JComponent editorComponent;
  private NodeRealizer realizer;

  public JourneyEditorWrapper(Editor editor, NodeRealizer realizer, PsiElement psiElement, Graph2DView view, JourneyDiagramDataModel dataModel) {
    super(new BorderLayout());
    editorComponent = editor.getComponent();
    this.realizer = realizer;
    setVisible(false);
    setPreferredSize(new Dimension(editor.getComponent().getWidth(), editor.getComponent().getHeight()));

    Runnable runnable1 = () -> {
      foldOnlyRange(editor, psiElement, false);
    };
    Runnable runnable2 = () -> {
      foldOnlyRange(editor, psiElement, true);
    };
    Runnable runnable3 = () -> {
    };

    editorComponent.add(new JourneyTitleBar(ObjectUtils.notNull(getQualifiedName(psiElement), () -> "No title"), editor, List.of(runnable1, runnable2, runnable3)), BorderLayout.NORTH);
    Border border = new JourneyLineBorder(JBColor.LIGHT_GRAY, 1, this, view);
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

  public NodeRealizer getRealizer() {
    return realizer;
  }
}