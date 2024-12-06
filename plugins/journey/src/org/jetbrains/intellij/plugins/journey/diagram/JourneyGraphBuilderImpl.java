package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramDataModel;
import com.intellij.diagram.DiagramPresentationModel;
import com.intellij.openapi.graph.threading.GraphThreadingType;
import com.intellij.openapi.graph.view.*;
import com.intellij.openapi.project.Project;
import com.intellij.uml.UmlGraphBuilder;
import org.jetbrains.annotations.NotNull;

public class JourneyGraphBuilderImpl extends UmlGraphBuilder {
  protected JourneyGraphBuilderImpl(@NotNull Project project,
                                    @NotNull Graph2D graph,
                                    @NotNull Graph2DView view,
                                    @NotNull DiagramDataModel<?> dataModel,
                                    @NotNull GraphThreadingType threadingType,
                                    @NotNull DiagramPresentationModel presentationModel) {
    super(project, graph, view, dataModel, threadingType, presentationModel);
  }

  @Override
  protected void setupViewControllers(@NotNull EditMode editMode) {
    final var magnifierViewMode = new MagnifierViewMode();
    // TODO remove magnifierViewMode completely
    magnifierViewMode.setMagnifierRadius(1);

    StandardGraphViewControllerBuilder
      .beginSetupFor(this, getGraphBuilderDispatcher())
      .setEditMode(editMode)
      .setMagnifierViewMode(magnifierViewMode)
      .endSetup();
  }
}
