package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.uml.core.renderers.DefaultUmlRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorWrapper;

import javax.swing.*;

import static org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager.updateEditorSize;

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

  @Override
  protected @NotNull JComponent createNodeRealizerComponent(@NotNull Graph2DView view,
                                                            @NotNull NodeRealizer realizer,
                                                            @Nullable Object object,
                                                            boolean isSelected) {
    final var node = myBuilder.getNodeObject(realizer.getNode());
    if (node instanceof JourneyNode journeyNode) {
      JourneyNodeIdentity identity = journeyNode.getIdentifyingElement();
      PsiMember psiMember = identity.getMember();
      PsiFile psiFile = identity.getFile();
      JourneyEditorWrapper cached = myDataModel.myEditorManager.NODE_PANELS.get(psiFile);
      if (cached != null) {
        cached.updateRealizer(realizer);
        return cached;
      }

      Editor editor = myDataModel.myEditorManager.openEditor(psiFile);
      updateEditorSize(editor, psiMember, (float)view.getZoom(), true);
      if (editor == null) {
        throw new IllegalStateException("Can't open " + psiFile);
      }
      editor.putUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL, myDataModel);

      JourneyEditorWrapper editorWrapper = new JourneyEditorWrapper(editor, journeyNode, realizer, psiMember, view);
      myDataModel.myEditorManager.onNodeClosed(identity, () -> {
        view.getCanvasComponent().remove(editorWrapper.getEditorComponent());
      });

      view.getCanvasComponent().add(editorWrapper.getEditorComponent());
      journeyNode.setEditor(editor);
      AsyncEditorLoader.Companion.performWhenLoaded(editor, () -> {
        journeyNode.addElement(psiMember);
      });
      myDataModel.myEditorManager.NODE_PANELS.put(psiFile, editorWrapper);
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
    var bounds = ((JourneyEditorWrapper)(component)).getDrawableRect(view);
    if (bounds.getWidth() >= view.getComponent().getWidth() && bounds.getHeight() >= view.getComponent().getHeight()) {
      view.setZoom(view.getZoom() * 0.9);
      view.updateView();
      return;
    }
    JComponent editorComponent = ((JourneyEditorWrapper)(component)).getEditorComponent();
    editorComponent.setVisible(true);
    editorComponent.setBounds(bounds);
    editorComponent.revalidate();
  }
}
