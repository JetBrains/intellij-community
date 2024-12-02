package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.openapi.graph.base.Edge;
import com.intellij.openapi.graph.base.Node;
import com.intellij.openapi.graph.builder.GraphBuilder;
import com.intellij.openapi.graph.services.GraphLayoutService;
import com.intellij.openapi.graph.services.GraphUndoService;
import com.intellij.openapi.graph.util.Futures;
import com.intellij.openapi.graph.view.Graph2D;
import com.intellij.openapi.graph.view.NodeRealizer;

import java.util.*;

public final class JourneyDiagramLayout {

  public static final double LAYOUT_OFFSET = 800;

  private static void layout(GraphBuilder<?, ?> builder, List<Node> nodesToLayout, List<Edge> edgesToLayout) {
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

  public static Optional<NodeRealizer> getRealizer(DiagramBuilder builder, JourneyNode journeyNode) {
    Graph2D graph2D = builder.getGraphBuilder().getGraph();
    Optional<Node> node = Arrays.stream(graph2D.getNodeArray()).filter(
      (n) -> Objects.equals(builder.getNodeObject(n), journeyNode)).findFirst();
    return node.map(graph2D::getRealizer);
  }

  public static void addEdgeElementLayout(DiagramBuilder builder, JourneyNode fromJourneyNode, JourneyNode toJourneyNode, boolean isLeftToRight) {
      var edgesToLayout = List.of(builder.getGraphBuilder().getGraph().getEdgeArray());
      List<Node> nodesToLayout = new ArrayList<>();
      var fromNode = getRealizer(builder, fromJourneyNode);
      var toNode = getRealizer(builder, toJourneyNode);
      var existedNode = isLeftToRight ? fromNode : toNode;
      var newNode = isLeftToRight ? toNode : fromNode;
      var offset = isLeftToRight ? LAYOUT_OFFSET : -LAYOUT_OFFSET;

      if (existedNode.isPresent() && newNode.isPresent()) {
        newNode.get().setCenterX(existedNode.get().getCenterX() + offset);
        newNode.get().setCenterY(existedNode.get().getCenterY());
        nodesToLayout = List.of(newNode.get().getNode());
      }
      layout(builder.getGraphBuilder(), nodesToLayout, edgesToLayout);
  }

  public static void addElementLayout(DiagramBuilder builder, JourneyNode node) {
    var edgesToLayout = List.of(builder.getGraphBuilder().getGraph().getEdgeArray());
    var nodeRealizer = getRealizer(builder, node);
    if (nodeRealizer.isPresent()) {
      var nodesToLayout = List.of(nodeRealizer.get().getNode());
      layout(builder.getGraphBuilder(), nodesToLayout, edgesToLayout);
    }
  }
}
