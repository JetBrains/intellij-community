package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.diagram.actions.DiagramAddElementAction;
import com.intellij.diagram.actions.DiagramDefaultAddElementAction;
import com.intellij.diagram.extras.DiagramExtras;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.openapi.graph.layout.LayoutOrientation;
import com.intellij.openapi.graph.layout.Layouter;
import com.intellij.openapi.graph.services.GraphLayoutService;
import com.intellij.openapi.graph.settings.GraphSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JourneyDiagramExtras extends DiagramExtras<JourneyNodeIdentity> {
  @Override
  public DiagramAddElementAction getAddElementHandler() {
    return new DiagramDefaultAddElementAction() {
      @Override
      protected @Nullable JourneyNodeIdentity convertElement(@NotNull Object element) {
        PsiElement psiElement = null;
        if (element instanceof JourneyNodeIdentity identity) {
          return identity;
        }
        if (element instanceof JourneyNode node) {
          return node.getIdentifyingElement();
        }
        if (element instanceof PsiElement psiElement1) {
          psiElement = psiElement1;
        }
        if (psiElement != null) {
          return new JourneyNodeIdentity(psiElement);
        }
        return null;
      }

      /**
       * Allows adding any symbols (methods, fields) to diagram along with classes via "Add Element Action (Space)".
       */
      @Override
      protected @NotNull ChooseByNameModel createModel(@NotNull DiagramBuilder builder) {
        return new GotoSymbolModel2(builder.getProject(), builder);
      }
    };
  }

  @Override
  public @Nullable Layouter getCustomLayouter(GraphSettings settings, Project project) {
    var layouter = GraphLayoutService.getInstance().getGroupLayouter();
    layouter.setLayoutOrientation(LayoutOrientation.LEFT_TO_RIGHT);
    return layouter;
  }

  /**
   * Used when exporting to file.
   * @see com.intellij.uml.core.actions.fs.SaveDiagramAction
   */
  @Override
  public @Nullable String suggestDiagramFileName(JourneyNodeIdentity element) {
    return element.getMember().getName();
  }
}
