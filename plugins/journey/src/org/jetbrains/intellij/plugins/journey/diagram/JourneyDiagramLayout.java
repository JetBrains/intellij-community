package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.openapi.graph.base.Edge;
import com.intellij.openapi.graph.base.Node;
import com.intellij.openapi.graph.builder.GraphBuilder;
import com.intellij.openapi.graph.services.GraphLayoutService;
import com.intellij.openapi.graph.services.GraphUndoService;
import com.intellij.openapi.graph.util.Futures;

import java.util.List;
import java.util.Objects;

public class JourneyDiagramLayout {

  public static final double LAYOUT_OFFSET = 800;

  public static void layout(GraphBuilder<?, ?> builder, List<Node> nodesToLayout, List<Edge> edgesToLayout) {
    final var layouter = builder.getGraphPresentationModel().getSettings().getCurrentLayouter();
    final var basicQuery = GraphLayoutService.getInstance().queryLayout(builder).animated().withLayouter(layouter);

    final var partialLayouter = GraphLayoutService.getInstance().getPartialLayouter(layouter, true);
    GraphLayoutService.getInstance().markElementsForPartialLayout(builder.getGraph(), nodesToLayout, edgesToLayout);
    final var adjustedQuery = basicQuery
      .withLayouter(partialLayouter)
      .withFitContent(GraphLayoutService.GraphLayoutQueryParams.FitContentOption.NEVER);

    GraphLayoutService.getInstance()
      .calcLayoutAsync(builder, Objects.requireNonNull(adjustedQuery.getLayouter()))
      .thenAcceptAsync(
        layout -> GraphUndoService.getInstance().performPositionsOnlyChangingAction(
          builder,
          "JourneyLayout",
          () -> adjustedQuery.withCustomLayout(layout).run()
        ),
        Futures.inEdt()
      );
  }

  public static enum Direction {
    LEFT,
    RIGHT
  }
}
