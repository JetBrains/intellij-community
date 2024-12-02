package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramEdgeBase;
import com.intellij.diagram.DiagramNode;
import com.intellij.diagram.DiagramRelationshipInfo;
import com.intellij.diagram.DiagramRelationshipInfoAdapter;
import com.intellij.diagram.presentation.DiagramLineType;
import org.jetbrains.annotations.NotNull;

public class JourneyEdge extends DiagramEdgeBase<JourneyNodeIdentity> {
  JourneyEdge(@NotNull DiagramNode<JourneyNodeIdentity> source,
              @NotNull DiagramNode<JourneyNodeIdentity> target) {
    super(source, target, MANY_TO_ONE);
  }

  private static final DiagramRelationshipInfo MANY_TO_ONE = new DiagramRelationshipInfoAdapter.Builder()
    .setName("MANY_TO_ONE")
    .setLineType(DiagramLineType.SOLID)
    .setWidth(2)
    .setTargetArrow(DiagramRelationshipInfo.ANGLE)
    .create();
}
