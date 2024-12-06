package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramEdgeBase;
import com.intellij.diagram.DiagramNode;
import com.intellij.diagram.DiagramRelationshipInfoAdapter;
import com.intellij.diagram.presentation.DiagramLineType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

public class JourneyEdge extends DiagramEdgeBase<JourneyNodeIdentity> {
  JourneyEdge(@NotNull DiagramNode<JourneyNodeIdentity> source,
              @NotNull DiagramNode<JourneyNodeIdentity> target, SmartPsiElementPointer fromPSI, SmartPsiElementPointer toPSI) {
    super(source, target, new JourneyRelationshipExtendedInfo(fromPSI, toPSI));
  }

  JourneyEdge(@NotNull DiagramNode<JourneyNodeIdentity> source,
              @NotNull DiagramNode<JourneyNodeIdentity> target, String relation) {
    super(source, target, new JourneyRelationshipExtendedInfo(relation, source.getIdentifyingElement().getFile().getProject()));
  }

  static class JourneyRelationshipExtendedInfo extends DiagramRelationshipInfoAdapter {
    final static String SOURCE_HEADER = "$$$source$$$:";
    final static String TARGET_HEADER = "$$$target$$$:";

    private SmartPsiElementPointer target;
    private SmartPsiElementPointer source;

    public JourneyRelationshipExtendedInfo(SmartPsiElementPointer source, SmartPsiElementPointer target) {
      super("JOURNEY", DiagramLineType.SOLID, 2, NONE, ANGLE, (Label)null, null, null, null, null, null);
      this.source = source;
      this.target = target;
    }

    public JourneyRelationshipExtendedInfo(String relation, Project project) {
      super("JOURNEY", DiagramLineType.SOLID, 2, NONE, ANGLE, (Label)null, null, null, null, null, null);

      this.source = PsiUtil.createSmartPointer(PsiUtil.resolveElementByFQN(
        relation.substring(SOURCE_HEADER.length(), relation.indexOf(TARGET_HEADER)), project));
      this.target = PsiUtil.createSmartPointer(PsiUtil.resolveElementByFQN(
        relation.substring(relation.indexOf(TARGET_HEADER) + TARGET_HEADER.length()), project));
    }

    @Override
    public String toString() {
      return ReadAction.compute(() -> SOURCE_HEADER + PsiUtil.getQualifiedName(source.getElement()) +
                                      TARGET_HEADER + PsiUtil.getQualifiedName(target.getElement()));
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof JourneyRelationshipExtendedInfo other) {
        return source.equals(other.source) && target.equals(other.target);
      }
      return false;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof JourneyEdge other) {
      return mySource.equals(other.mySource) && myTarget.equals(other.myTarget) && myRelationship.equals(other.myRelationship);
    }
    return false;
  }

  public SmartPsiElementPointer getPsiSource() {
    return ((JourneyRelationshipExtendedInfo)myRelationship).source;
  }

  public SmartPsiElementPointer getPsiTarget() {
    return ((JourneyRelationshipExtendedInfo)myRelationship).target;
  }
}