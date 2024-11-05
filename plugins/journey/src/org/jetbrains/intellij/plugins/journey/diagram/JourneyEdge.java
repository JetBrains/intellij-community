package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramEdgeBase;
import com.intellij.diagram.DiagramNode;
import com.intellij.diagram.DiagramRelationshipInfo;
import org.jetbrains.annotations.NotNull;

public class JourneyEdge extends DiagramEdgeBase<JourneyNodeIdentity> {
  JourneyEdge(@NotNull DiagramNode<JourneyNodeIdentity> source,
              @NotNull DiagramNode<JourneyNodeIdentity> target,
              @NotNull DiagramRelationshipInfo relationship) {
    super(source, target, relationship);
  }
}
