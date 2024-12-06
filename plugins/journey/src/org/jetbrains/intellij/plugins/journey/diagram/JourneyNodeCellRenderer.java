package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.uml.core.renderers.DefaultUmlRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorWrapper;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import javax.swing.*;

import java.util.Objects;

import static org.jetbrains.intellij.plugins.journey.JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL;
import static org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager.updateEditorSize;
import static org.jetbrains.intellij.plugins.journey.util.PsiUtil.createSmartPointer;

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
      editor.putUserData(JOURNEY_DIAGRAM_DATA_MODEL, myDataModel);

      JourneyEditorWrapper editorWrapper = new JourneyEditorWrapper(editor, journeyNode, realizer, createSmartPointer(psiMember), view);
      myDataModel.myEditorManager.onNodeClosed(identity, () -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          view.getCanvasComponent().remove(editorWrapper.getEditorComponent());
          view.getCanvasComponent().revalidate();
          // TODO replace to removing editorWrapper.getEditorComponent() in view.getCanvasComponent()
        });
      });

      view.getCanvasComponent().add(editorWrapper.getEditorComponent());
      journeyNode.setEditor(editor);
      myDataModel.myEditorManager.NODE_PANELS.put(psiFile, editorWrapper);
      editor.getCaretModel().addCaretListener(new CaretListener() {
        @Override
        public void caretPositionChanged(@NotNull CaretEvent event) {
          PsiElement elementAtCaret = psiFile.findElementAt(event.getCaret().getOffset());
          var member = PsiUtil.tryFindParentOrNull(elementAtCaret, it -> it instanceof PsiMember);
          if (member != null) {
            var memberPointer = createSmartPointer(member);
            myDataModel.setCurrentPSIatCaret(memberPointer);
            editorWrapper.setTitle(memberPointer);
          } else {
            myDataModel.setCurrentPSIatCaret(null);
            editorWrapper.setTitle(null);
          }
          view.updateView();
          view.getGraph2D().updateViews();
        }
      });
      editor.getScrollingModel().addVisibleAreaListener(new VisibleAreaListener() {
        @Override
        public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
          view.updateView();
          view.getGraph2D().updateViews();
        }
      });
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
      return;
    }
    JComponent editorComponent = ((JourneyEditorWrapper)(component)).getEditorComponent();
    // TODO move to more appropriate place
    Objects.requireNonNull(((JourneyEditorWrapper)(component)).getEditor().getUserData(JOURNEY_DIAGRAM_DATA_MODEL)).highlightEdges();
    editorComponent.setBounds(bounds);
    editorComponent.setVisible(true);
    editorComponent.revalidate();
  }
}
