package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.diagram.DiagramElementManager;
import com.intellij.diagram.DiagramProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

// TODO
@SuppressWarnings("HardCodedStringLiteral")
public final class JourneyDiagramElementManager implements DiagramElementManager<JourneyNodeIdentity> {
  @Override
  public @Nullable JourneyNodeIdentity findInDataContext(@NotNull DataContext context) {
    return null;
  }

  @Override
  public @NotNull Collection<JourneyNodeIdentity> findElementsInDataContext(@NotNull DataContext context) {
    return List.of();
  }

  @Override
  public boolean isAcceptableAsNode(@Nullable Object element) {
    return element instanceof JourneyNodeIdentity;
  }

  @Override
  public Object @NotNull [] getNodeItems(JourneyNodeIdentity nodeElement) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public Object @NotNull [] getNodeItems(JourneyNodeIdentity nodeElement, @NotNull DiagramBuilder builder) {
    if (nodeElement == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final var model = builder.getDataModel();
    if (model instanceof JourneyDiagramDataModel journeyDataModel) {
      return journeyDataModel.getEdges().stream()
        .filter(it -> it.getIdentifyingElement() == nodeElement)
        .map(it -> it.getTarget())
        .toList()
        .toArray(new JourneyNode[0]);
    }
    return getNodeItems(nodeElement);
  }

  // Override to prevent stackoverflow error.
  @Override
  public @Nullable SimpleColoredText getItemType(@Nullable JourneyNodeIdentity nodeElement,
                                                 @Nullable Object nodeItem,
                                                 @Nullable DiagramBuilder builder) {
    return null;
  }

  @Override
  public boolean canCollapse(JourneyNodeIdentity element) {
    return false;
  }

  @Override
  public boolean isContainerFor(JourneyNodeIdentity container, JourneyNodeIdentity element) {
    return false;
  }

  @Override
  public @Nullable @Nls String getElementTitle(JourneyNodeIdentity element) {
    return "Journey dummy element title";
  }

  @Override
  public @Nullable @Nls String getNodeTooltip(JourneyNodeIdentity element) {
    return "Journey dummy node tooltip";
  }

  @Override
  public void setUmlProvider(@NotNull DiagramProvider<JourneyNodeIdentity> provider) {
  }
}
