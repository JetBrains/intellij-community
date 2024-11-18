package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.uml.core.renderers.DefaultUmlRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

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
  protected @NotNull ComponentsStoragePolicy getComponentsStoragePolicy() {
    return super.getComponentsStoragePolicy();
  }

  private final Map<PsiElement, JComponent> NODE_PANELS = new HashMap<>();

  @Override
  protected @NotNull JComponent createNodeRealizerComponent(@NotNull Graph2DView view,
                                                            @NotNull NodeRealizer realizer,
                                                            @Nullable Object object,
                                                            boolean isSelected) {

    final var node = myBuilder.getNodeObject(realizer.getNode());
    if (node instanceof JourneyNode journeyNode) {
      JourneyNodeIdentity id = journeyNode.getIdentifyingElement();
      PsiElement psiElement = id.calculatePsiElement();
      JComponent cached = NODE_PANELS.get(psiElement);
      if (cached != null) {
        HackyBoundsTranslationNodeComponent cached1 = (HackyBoundsTranslationNodeComponent)(cached);
        cached1.setView(view);
        cached1.setRealizer(realizer);
        return cached;
      }
      Editor editor = myDataModel.myEditorManager.getOpenedJourneyEditor(psiElement);
      if (editor != null) {
        throw new IllegalStateException("Editor already opened for " + psiElement);
      }
      JComponent component = null;
      editor = myDataModel.myEditorManager.openPsiElementInEditor(psiElement);
      if (editor != null) {
        editor.putUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL, myDataModel);
        component = editor.getComponent();
      }
      if (component == null) {
        throw new IllegalStateException("Can't open " + psiElement);
      }
      JourneyTitleBar titleBar = new JourneyTitleBar(getQualifiedName(psiElement), component);
      component.add(titleBar, BorderLayout.NORTH);

      Border border = BorderFactory.createLineBorder(JBColor.DARK_GRAY, 1);
      component.setBorder(border);

      HackyBoundsTranslationNodeComponent panel = new HackyBoundsTranslationNodeComponent(view, realizer);
      panel.add(component);
      panel.setSize(component.getSize());
      view.getCanvasComponent().add(panel);
      NODE_PANELS.put(psiElement, panel);
      Editor finalEditor = editor;
      myDataModel.myEditorManager.closeEditor.addListener(it -> {
        if (it == finalEditor) {
          view.getCanvasComponent().remove(panel);
        }
      });

      panel.setView(view);
      panel.setRealizer(realizer);
      return panel;
    }
    return super.createNodeRealizerComponent(view, realizer, object, isSelected);
  }

  @Override
  public void tuneNode(@NotNull NodeRealizer realizer, JPanel wrapper, Point point) {
    super.tuneNode(realizer, wrapper, point);
  }
}
