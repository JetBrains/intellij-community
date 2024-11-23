package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.uml.core.renderers.DefaultUmlRenderer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorWrapper;

import javax.swing.*;
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
  private final DiagramBuilder myBuilder;

  public JourneyNodeCellRenderer(@NotNull DiagramBuilder builder, @Nullable ModificationTracker updates) {
    super(builder, updates);
    myBuilder = builder;
    myDataModel = ((JourneyDiagramDataModel)builder.getDataModel());
  }

  private static final Map<PsiElement, JourneyEditorWrapper> NODE_PANELS = new HashMap<>();

  public static NodeRealizer getRealizer(@NotNull PsiElement psiElement) {
    return NODE_PANELS.get(psiElement).getRealizer();
  }

  @Override
  protected @NotNull JComponent createNodeRealizerComponent(@NotNull Graph2DView view,
                                                            @NotNull NodeRealizer realizer,
                                                            @Nullable Object object,
                                                            boolean isSelected) {
    final var node = myBuilder.getNodeObject(realizer.getNode());
    if (node instanceof JourneyNode journeyNode) {
      PsiElement psiElement = journeyNode.getIdentifyingElement().calculatePsiElement();
      JourneyEditorWrapper cached = NODE_PANELS.get(psiElement);
      if (cached != null) {
        cached.updateRealizer(realizer);
        return cached;
      }

      Editor editor = myDataModel.myEditorManager.openPsiElementInEditor(psiElement, (float)view.getZoom());
      if (editor == null) {
        throw new IllegalStateException("Can't open " + psiElement);
      }
      editor.putUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL, myDataModel);

      JourneyEditorWrapper editorWrapper = new JourneyEditorWrapper(editor, realizer, psiElement, view, myDataModel);
      myDataModel.myEditorManager.closeEditor.addListener(it -> {
        if (it == editor) {
          view.getCanvasComponent().remove(editorWrapper.getEditorComponent());
          NODE_PANELS.remove(psiElement);
        }
      });

      view.getCanvasComponent().add(editorWrapper.getEditorComponent());
      NODE_PANELS.put(psiElement, editorWrapper);
      return editorWrapper;
    }
    return super.createNodeRealizerComponent(view, realizer, object, isSelected);
  }

  @Override
  protected void beforeShown(
    @NotNull JComponent component,
    @NotNull Graph2DView view,
    @NotNull NodeRealizer nodeRealizer,
    @Nullable Object object,
    boolean isSelected
  ) {
    JComponent editorComponent = ((JourneyEditorWrapper)(component)).getEditorComponent();
    editorComponent.setVisible(true);
    editorComponent.setBounds(((JourneyEditorWrapper)(component)).getDrawableRect(view));
    editorComponent.revalidate();
  }
}
