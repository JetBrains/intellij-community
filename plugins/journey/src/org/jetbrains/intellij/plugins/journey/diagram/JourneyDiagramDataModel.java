package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.*;
import com.intellij.diagram.presentation.DiagramLineType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.graph.base.Edge;
import com.intellij.openapi.graph.base.Node;
import com.intellij.openapi.graph.builder.GraphBuilder;
import com.intellij.openapi.graph.services.GraphLayoutService;
import com.intellij.openapi.graph.services.GraphUndoService;
import com.intellij.openapi.graph.util.Futures;
import com.intellij.openapi.graph.view.Graph2D;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager;
import org.jetbrains.intellij.plugins.journey.util.JourneyNavigationUtil;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.*;

public final class JourneyDiagramDataModel extends DiagramDataModel<JourneyNodeIdentity> {

  private final DiagramProvider<JourneyNodeIdentity> myProvider;
  private final Collection<JourneyNode> myNodes = new HashSet<>();
  private final List<JourneyEdge> myEdges = new ArrayList<>();
  public final JourneyEditorManager myEditorManager;
  private static final double LAYOUT_OFFSET = 800;
  private static JourneyDiagramDataModel currentModel;

  public JourneyDiagramDataModel(
    @NotNull Project project,
    @NotNull DiagramProvider<JourneyNodeIdentity> provider
  ) {
    this(project, provider, null);
  }

  public JourneyDiagramDataModel(
    @NotNull Project project,
    @NotNull DiagramProvider<JourneyNodeIdentity> provider,
    @Nullable DiagramNodeContentManager nodeContentManager
  ) {
    super(project, provider, nodeContentManager);
    myProvider = provider;
    myEditorManager = new JourneyEditorManager();
    Disposer.register(this, myEditorManager);
    currentModel = this;
  }

  public JourneyDiagramDataModel(
    @NotNull Project project,
    @NotNull DiagramProvider<JourneyNodeIdentity> provider,
    @NotNull DiagramVisibilityManager visibilityManager,
    @NotNull DiagramItemOrderingManager itemOrderingManager,
    @Nullable DiagramScopeManager<JourneyNodeIdentity> scopeManager,
    @Nullable DiagramNodeContentManager nodeContentManager
  ) {
    super(project, provider, visibilityManager, itemOrderingManager, scopeManager, nodeContentManager);
    myProvider = provider;
    myEditorManager = new JourneyEditorManager();
    Disposer.register(this, myEditorManager);
    currentModel = this;
  }

  public static JourneyDiagramDataModel getCurrentModel() {
    return currentModel;
  }

  @Override
  public @NotNull ModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  @Override
  public @NotNull List<JourneyNode> getNodes() {
    return myNodes.stream().toList();
  }

  @Override
  public @NotNull String getNodeName(@NotNull DiagramNode<JourneyNodeIdentity> n) {
    String fqn = n.getQualifiedName();
    if (fqn == null) {
      return new Random().nextInt(1000) + "node random name";
    }
    return fqn;
  }

  @Override
  public @NotNull JourneyNode addElement(@Nullable JourneyNodeIdentity element) {
    if (element == null) throw new IllegalArgumentException("element must not be null");
    PsiElement psiElement = element.element();
    psiElement = PsiUtil.tryFindParentOrNull(psiElement, it -> it instanceof PsiMember);
    if (psiElement == null) throw new IllegalArgumentException("element must be a member");
    JourneyNodeIdentity identity = new JourneyNodeIdentity(psiElement);
    String title = myProvider.getVfsResolver().getQualifiedName(identity);
    JourneyNode node = new JourneyNode(myProvider, identity, title);
    myNodes.add(node);
    return node;
  }

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

  private static Optional<NodeRealizer> getRealizer(DiagramBuilder builder, JourneyNode journeyNode) {
    Graph2D graph2D = builder.getGraphBuilder().getGraph();
    Optional<Node> node = Arrays.stream(graph2D.getNodeArray()).filter(
      (n) -> Objects.equals(builder.getNodeObject(n), journeyNode)).findFirst();
    return node.map(graph2D::getRealizer);
  }

  public boolean isNodeExist(JourneyNode node) {
    return getRealizer(this.getBuilder(), node).isPresent();
  }

  public void addNewPairUpdate(JourneyNode fromJourneyNode, JourneyNode toJourneyNode, boolean isLeftToRight) {
    queryUpdate(() -> {
      var edgesToLayout = List.of(getBuilder().getGraphBuilder().getGraph().getEdgeArray());
      List<Node> nodesToLayout = new ArrayList<>();
      var fromNode = getRealizer(this.getBuilder(), fromJourneyNode);
      var toNode = getRealizer(this.getBuilder(), toJourneyNode);
      var existedNode = isLeftToRight ? fromNode : toNode;
      var newNode = isLeftToRight ? toNode : fromNode;
      var offset = isLeftToRight ? LAYOUT_OFFSET : -LAYOUT_OFFSET;

      if (existedNode.isPresent() && newNode.isPresent()) {
        newNode.get().setCenterX(existedNode.get().getCenterX() + offset);
        newNode.get().setCenterY(existedNode.get().getCenterY());
        nodesToLayout = List.of(newNode.get().getNode());
      }
      layout(getBuilder().getGraphBuilder(), nodesToLayout, edgesToLayout);
    });
  }

  public void addElementWithLayout(PsiElement element) {
    var node = Optional.of(addElement(new JourneyNodeIdentity(element)));
    queryUpdate(() -> {
      var edgesToLayout = List.of(getBuilder().getGraphBuilder().getGraph().getEdgeArray());
      var nodeRealizer = getRealizer(this.getBuilder(), node.get());
      if (nodeRealizer.isPresent()) {
        var nodesToLayout = List.of(nodeRealizer.get().getNode());
        layout(getBuilder().getGraphBuilder(), nodesToLayout, edgesToLayout);
      }
    });
  }

  public void queryUpdate(Runnable thenRun) {
    ApplicationManager.getApplication().invokeLater(() -> {
      getBuilder().queryUpdate()
        .withAllNodeSizeUpdate()
        .withDataReload()
        .withNodePresentationsUpdate(true)
        .runAsync()
        .thenRun(thenRun);
    });
  }

  @Override
  public void rebuild(JourneyNodeIdentity element) {
    refreshDataModel();
  }

  @Override
  public void removeNode(@NotNull DiagramNode<JourneyNodeIdentity> node) {
    myNodes.removeIf(it -> Objects.equals(it, node));
    myEdges.removeIf(it -> it.getSource().equals(node) ||
                           it.getTarget().equals(node));
    myEditorManager.closeNode(node.getIdentifyingElement().element());
    queryUpdate(() -> {});
  }

  @Override
  public @Nullable DiagramEdge<JourneyNodeIdentity> createEdge(@NotNull DiagramNode<JourneyNodeIdentity> from,
                                                               @NotNull DiagramNode<JourneyNodeIdentity> to) {
    if (from.equals(to)) return null;
    JourneyEdge edge = new JourneyEdge(from, to, MANY_TO_ONE);
    myEdges.add(edge);
    return edge;
  }

  public static final DiagramRelationshipInfo MANY_TO_ONE = new DiagramRelationshipInfoAdapter.Builder()
    .setName("MANY_TO_ONE")
    .setLineType(DiagramLineType.SOLID)
    .setWidth(2)
    .setTargetArrow(DiagramRelationshipInfo.ANGLE)
    .create();

  @Override
  public void removeEdge(@NotNull DiagramEdge<JourneyNodeIdentity> edge) {
    myEdges.removeIf(it -> it.equals(edge));
  }

  @Override
  public @NotNull List<JourneyEdge> getEdges() {
    return myEdges;
  }

  @Override
  public void dispose() {
    myNodes.clear();
    myEdges.clear();
  }

  public void addEdge(Object from, Object to) {
    PsiElement fromResult = JourneyNavigationUtil.findPsiElement(getProject(), from);
    PsiElement toResult = JourneyNavigationUtil.findPsiElement(getProject(), to);
    JourneyDiagramProvider.addEdge(fromResult, toResult, this);
  }

  public void addEdgeAsync(Object from, Object to) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> addEdge(from, to));
  }
}
