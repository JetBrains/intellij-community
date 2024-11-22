package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.*;
import com.intellij.diagram.presentation.DiagramLineType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager;
import org.jetbrains.intellij.plugins.journey.navigation.JourneyNavigationUtils;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.*;

public final class JourneyDiagramDataModel extends DiagramDataModel<JourneyNodeIdentity> {

  private final DiagramProvider<JourneyNodeIdentity> myProvider;
  private final Collection<JourneyNode> myNodes = new HashSet<>();
  private @NotNull List<JourneyEdge> myEdges = new ArrayList<>();
  public final JourneyEditorManager myEditorManager;
  private static final double LAYOUT_OFFSET = 400;

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
  public @Nullable JourneyNode addElement(@Nullable JourneyNodeIdentity element) {
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

  private void layout(GraphBuilder<?,?> builder, List<Node> nodesToLayout, List<Edge> edgesToLayout) {
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

  private Optional<NodeRealizer> getRealizer(DiagramBuilder builder, JourneyNode journeyNode) {
    Graph2D graph2D = builder.getGraphBuilder().getGraph();
    Optional<Node> node = Arrays.stream(graph2D.getNodeArray()).filter(
      (n) -> Objects.equals(builder.getNodeObject(n), journeyNode)).findFirst();
    return node.map(graph2D::getRealizer);
  }

  private void layoutNewElement(JourneyNode fromJourneyNode, JourneyNode toJourneyNode) {
    var edgesToLayout = List.of(getBuilder().getGraphBuilder().getGraph().getEdgeArray());
    List<Node> nodesToLayout = new ArrayList<>();
    var fromNode = getRealizer(this.getBuilder(), fromJourneyNode);
    var toNode = getRealizer(this.getBuilder(), toJourneyNode);

    if (fromNode.isPresent() && toNode.isPresent()) {
      toNode.get().setCenterX(fromNode.get().getCenterX() + LAYOUT_OFFSET);
      toNode.get().setCenterY(fromNode.get().getCenterY());
      nodesToLayout = List.of(toNode.get().getNode());
    }
    layout(getBuilder().getGraphBuilder(), nodesToLayout, edgesToLayout);
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
    .setTargetArrow(DiagramRelationshipInfo.CROWS_FOOT_MANY)
    .setSourceArrow(DiagramRelationshipInfo.ANGLE)
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

  }

  public void addEdge(Object from, Object to) {
    PsiElement fromResult = JourneyNavigationUtils.findPsiElement(getProject(), from);
    PsiElement toResult = JourneyNavigationUtils.findPsiElement(getProject(), to);
    addEdge(fromResult, toResult);
  }

  public void addEdge(PsiElement from, PsiElement to) {
    var fromNode = Optional.ofNullable(findNode(from)).orElseGet(() -> addElement(new JourneyNodeIdentity(from)));
    var toNode = Optional.ofNullable(findNode(to)).orElseGet(() -> addElement(new JourneyNodeIdentity(to)));
    if (toNode == null || fromNode == null) {
      return;
    }

    createEdge(fromNode, toNode);
    queryUpdate(() -> {
      layoutNewElement(toNode, fromNode);
    });
  }

  private @Nullable JourneyNode findNode(PsiElement from) {
    if (from == null) return null;
    PsiFile fromFile = ReadAction.nonBlocking(() -> from.getContainingFile()).executeSynchronously();
    return ContainerUtil.find(this.getNodes(), node -> {
      PsiElement element = node.getIdentifyingElement().element();
      PsiFile toFile = ReadAction.nonBlocking(() -> element.getContainingFile()).executeSynchronously();
      return toFile.isEquivalentTo(fromFile);
    });
  }

}
