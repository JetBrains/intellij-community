package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramDataModel;
import com.intellij.diagram.DiagramPresentationModel;
import com.intellij.diagram.DiagramProvider;
import com.intellij.openapi.graph.view.Graph2D;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.project.Project;
import com.intellij.uml.UmlGraphBuilder;
import com.intellij.uml.UmlGraphBuilderFactory;
import org.jetbrains.annotations.NotNull;

public class JourneyGraphBuilderFactory extends UmlGraphBuilderFactory {
  @Override
  protected <T> @NotNull UmlGraphBuilder createBuilder(
    @NotNull Project project,
    @NotNull DiagramProvider<T> provider,
    @NotNull Graph2D graph,
    @NotNull Graph2DView view,
    @NotNull DiagramPresentationModel presentationModel,
    @NotNull DiagramDataModel<T> dataModel
  ) {
    return new JourneyGraphBuilderImpl(project, graph, view, dataModel, provider.getExtras().getThreadingType(), presentationModel);
  }
}
