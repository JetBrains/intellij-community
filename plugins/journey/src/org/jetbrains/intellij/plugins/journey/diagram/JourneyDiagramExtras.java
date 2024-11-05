package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.actions.DiagramAddElementAction;
import com.intellij.diagram.actions.DiagramDefaultAddElementAction;
import com.intellij.diagram.extras.DiagramExtras;
import com.intellij.openapi.graph.layout.LayoutOrientation;
import com.intellij.openapi.graph.layout.Layouter;
import com.intellij.openapi.graph.services.GraphLayoutService;
import com.intellij.openapi.graph.settings.GraphSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

public class JourneyDiagramExtras extends DiagramExtras<JourneyNodeIdentity> {
  @Override
  public DiagramAddElementAction getAddElementHandler() {
    return new DiagramDefaultAddElementAction() {
      @Override
      protected @Nullable Object convertElement(@NotNull Object element) {
        PsiElement psiElement = null;
        if (element instanceof JourneyNodeIdentity element2) {
          psiElement = element2.calculatePsiElement();
        }
        if (element instanceof JourneyNode node1) {
          psiElement = node1.getIdentifyingElement().calculatePsiElement();
        }
        if (element instanceof PsiElement psiElement1) {
          psiElement = psiElement1;
        }
        psiElement = PsiUtil.tryFindParentOrNull(psiElement, it -> it instanceof PsiMember);
        if (psiElement != null) {
          return new JourneyNodeIdentity(psiElement);
        }
        return null;
      }
    };
  }

  @Override
  public @Nullable Layouter getCustomLayouter(GraphSettings settings, Project project) {
    var layouter = GraphLayoutService.getInstance().getGroupLayouter();
    layouter.setLayoutOrientation(LayoutOrientation.LEFT_TO_RIGHT);
    return layouter;
  }

}
