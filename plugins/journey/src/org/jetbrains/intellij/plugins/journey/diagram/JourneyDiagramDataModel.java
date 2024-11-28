package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.*;
import com.intellij.diagram.presentation.DiagramLineType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.graph.base.Node;
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
import org.jetbrains.intellij.plugins.journey.util.JourneyNavigationUtil;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.*;

import static org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramLayout.LAYOUT_OFFSET;
import static org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramLayout.layout;

public final class JourneyDiagramDataModel extends DiagramDataModel<JourneyNodeIdentity> {

  private final DiagramProvider<JourneyNodeIdentity> myProvider;
  private final Collection<JourneyNode> myNodes = new HashSet<>();
  private final List<JourneyEdge> myEdges = new ArrayList<>();
  public final JourneyEditorManager myEditorManager;

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
  public @NotNull JourneyNode addElement(@Nullable JourneyNodeIdentity identity) {
    if (identity == null) throw new IllegalArgumentException("element must not be null");

    String title = myProvider.getVfsResolver().getQualifiedName(identity);
    JourneyNode node = new JourneyNode(myProvider, identity, title);
    myNodes.add(node);
    return node;
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

  public void addNewPairUpdate(JourneyNode fromJourneyNode, JourneyNode toJourneyNode, boolean isLeftToRight, boolean needLayout) {
    queryUpdate(() -> {
      if (!needLayout) {
        return;
      }

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
    List<JourneyEdge> edgesFromNode = findEdgesFrom(node.getIdentifyingElement());
    List<JourneyEdge> edgesToNode = findEdgesTo(node.getIdentifyingElement());
    if (edgesToNode.size() == 1 && edgesFromNode.size() == 1) {
      createEdge(edgesToNode.get(0).getSource(), edgesFromNode.get(0).getTarget());
    }
    myEdges.removeAll(edgesFromNode);
    myEdges.removeAll(edgesToNode);
    myEditorManager.closeNode(node.getIdentifyingElement().calculatePsiElement());
    myNodes.removeIf(it -> Objects.equals(it, node));
    queryUpdate(() -> {});
  }

  public List<JourneyEdge> findEdgesFrom(JourneyNodeIdentity nodeIdentity) {
    return myEdges.stream().filter(it -> it.getSource().equals(getNode(nodeIdentity))).toList();
  }

  public List<JourneyEdge> findEdgesTo(JourneyNodeIdentity nodeIdentity) {
    return myEdges.stream().filter(it -> it.getTarget().equals(getNode(nodeIdentity))).toList();
  }

  public @Nullable JourneyNode getNode(JourneyNodeIdentity nodeIdentity) {
    return ContainerUtil.find(myNodes, it -> it.getIdentifyingElement().equals(nodeIdentity));
  }

  @Override
  public @Nullable DiagramEdge<JourneyNodeIdentity> createEdge(@NotNull DiagramNode<JourneyNodeIdentity> from,
                                                               @NotNull DiagramNode<JourneyNodeIdentity> to) {
    if (from.equals(to)) return null;
    JourneyEdge edge = new JourneyEdge(from, to, MANY_TO_ONE);
    if (!myEdges.contains(edge)) {
      myEdges.add(edge);
    }
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
