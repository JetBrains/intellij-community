package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.uml.core.renderers.DefaultUmlRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramVfsResolver.getQualifiedName;

/**
 * There is no specific reason to inherit {@link DefaultUmlRenderer}, need to write custom implementation later,
 * which will have nothing in common with {@link DefaultUmlRenderer}.
 * It looks like we use it for no reason and hardly override all the implementation of {@link DefaultUmlRenderer}.
 */
public class JourneyNodeCellRenderer extends DefaultUmlRenderer {
  private final JourneyDiagramDataModel myDataModel;

  public JourneyNodeCellRenderer(@NotNull DiagramBuilder builder, @Nullable ModificationTracker updates) {
    super(builder, updates);
    myDataModel = ((JourneyDiagramDataModel)myBuilder.getDataModel());
  }

  @Override
  protected @NotNull JComponent createNodeRealizerComponent(@NotNull Graph2DView view,
                                                            @NotNull NodeRealizer realizer,
                                                            @Nullable Object object,
                                                            boolean isSelected) {
    final var node = myBuilder.getNodeObject(realizer.getNode());
    if (node instanceof JourneyNode journeyNode) {
      JourneyNodeIdentity id = journeyNode.getIdentifyingElement();
      PsiElement psiElement = id.calculatePsiElement();
      JComponent component = myDataModel.myEditorManager.getOpenedJourneyComponent(psiElement);
      if (component != null) {
        return component;
      }
      component = myDataModel.myEditorManager.openPsiElementInEditor(psiElement);
      if (component == null) {
        throw new IllegalStateException("Can't open " + psiElement);
      }
      JourneyTitleBar titleBar = new JourneyTitleBar(getQualifiedName(psiElement));
      component.add(titleBar, BorderLayout.NORTH);

      view.getCanvasComponent().add(component);
      var component1 = component;
      myDataModel.myEditorManager.closeComponent.addListener(it -> {
        if (it == component1) {
          view.getCanvasComponent().remove(component1);
        }
      });
      return component;
    }
    return super.createNodeRealizerComponent(view, realizer, object, isSelected);
  }
}
