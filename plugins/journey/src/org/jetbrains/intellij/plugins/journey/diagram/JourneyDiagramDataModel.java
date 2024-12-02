package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.graph.view.Graph2DView;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorWrapper;
import org.jetbrains.intellij.plugins.journey.util.JourneyNavigationUtil;

import javax.swing.Timer;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramLayout.getRealizer;

public final class JourneyDiagramDataModel extends DiagramDataModel<JourneyNodeIdentity> {

  private final DiagramProvider<JourneyNodeIdentity> myProvider;
  private final List<JourneyNode> myNodes = new ArrayList<>();
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
    return myNodes;
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
  public @NotNull JourneyNode addElement(@NotNull JourneyNodeIdentity identity) {
    String title = myProvider.getVfsResolver().getQualifiedName(identity);
    JourneyNode node = new JourneyNode(myProvider, identity, title);
    myNodes.add(node);
    return node;
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

  @Override
  public @Nullable DiagramEdge<JourneyNodeIdentity> createEdge(@NotNull DiagramNode<JourneyNodeIdentity> from,
                                                               @NotNull DiagramNode<JourneyNodeIdentity> to) {
    if (from.equals(to)) return null;
    JourneyEdge edge = new JourneyEdge(from, to);
    if (!myEdges.contains(edge)) {
      myEdges.add(edge);
    }
    return edge;
  }

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

  public boolean isNodeExist(JourneyNode node) {
    // TODO replace to myNodes.contains()
    return getRealizer(this.getBuilder(), node).isPresent();
  }

  public void addNewEdgeElementUpdate(JourneyNode fromJourneyNode, JourneyNode toJourneyNode, boolean isLeftToRight) {
    queryUpdate(() -> {
      JourneyDiagramLayout.addEdgeElementLayout(getBuilder(), fromJourneyNode, toJourneyNode, isLeftToRight);
    });
  }

  public void addElementUpdate(PsiElement element) {
    var node = Optional.of(addElement(new JourneyNodeIdentity(element)));
    queryUpdate(() -> {
      JourneyDiagramLayout.addElementLayout(getBuilder(), node.get());
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

  public void addEdgeAsync(Object from, Object to) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> addEdge(from, to));
  }

  private List<JourneyEdge> findEdgesFrom(JourneyNodeIdentity nodeIdentity) {
    return myEdges.stream().filter(it -> it.getSource().equals(getNode(nodeIdentity))).toList();
  }

  private List<JourneyEdge> findEdgesTo(JourneyNodeIdentity nodeIdentity) {
    return myEdges.stream().filter(it -> it.getTarget().equals(getNode(nodeIdentity))).toList();
  }

  private @Nullable JourneyNode getNode(JourneyNodeIdentity nodeIdentity) {
    return ContainerUtil.find(myNodes, it -> it.getIdentifyingElement().equals(nodeIdentity));
  }

  private static void animateTransition(Graph2DView view, Point2D target) {
    AtomicInteger iteration = new AtomicInteger(100);
    Point2D offset = new Point2D.Double(
      (target.getX() - view.getCenter().getX()) / iteration.get(), (target.getY() - view.getCenter().getY()) / iteration.get());
    javax.swing.Timer timer = new javax.swing.Timer(500 / iteration.get(), e -> {
      view.setCenter(view.getCenter().getX() + offset.getX(), view.getCenter().getY() + offset.getY());
      view.updateView();
      if (iteration.getAndDecrement() < 0) {
        ((Timer)e.getSource()).stop();

      }
    });
    timer.setRepeats(true);
    timer.start();
  }

  private @Nullable JourneyNode findNodeForFile(PsiElement from) {
    if (from == null) return null;
    return ReadAction.compute(() -> {
      PsiFile fromFile = ReadAction.nonBlocking(() -> from.getContainingFile()).executeSynchronously();
      return ContainerUtil.find(getNodes(), node -> {
        PsiFile toFile = node.getIdentifyingElement().calculatePsiElement();
        return toFile.isEquivalentTo(fromFile);
      });
    });
  }


  private void addEdge(Object from, Object to) {
    PsiElement fromPSI = JourneyNavigationUtil.findPsiElement(getProject(), from);
    PsiElement toPSI = JourneyNavigationUtil.findPsiElement(getProject(), to);
    if (fromPSI == null || toPSI == null) {
      // TODO add info to logger
      return;
    }
    var fromNode = Optional.ofNullable(findNodeForFile(fromPSI)).orElseGet(() -> addElement(new JourneyNodeIdentity(fromPSI)));
    var toNode = Optional.ofNullable(findNodeForFile(toPSI)).orElseGet(() -> addElement(new JourneyNodeIdentity(toPSI)));

    createEdge(fromNode, toNode);
    boolean isNewNode = !isNodeExist(fromNode) || !isNodeExist(toNode);
    if (isNewNode) {
      boolean isLeftToRight = isNodeExist(fromNode);
      addNewEdgeElementUpdate(fromNode, toNode, isLeftToRight);
    } else {
      queryUpdate(() -> {});
    }
    toNode.addElement(toPSI);
    fromNode.addElement(fromPSI);
    if (!isNewNode) {
      ApplicationManager.getApplication().invokeLater(() -> {
        JourneyEditorWrapper editor = myEditorManager.NODE_PANELS.get(fromNode.getIdentifyingElement().calculatePsiElement());
        boolean isLeftToRight = (IdeFocusManager.getInstance(editor.editor.getProject()).
                                   getFocusedDescendantFor(editor.getEditorComponent()) != null);
        Optional<NodeRealizer> realizer = getRealizer(getBuilder(), isLeftToRight ? toNode : fromNode);
        realizer.ifPresent(nodeRealizer -> animateTransition(getBuilder().getView(),
                           new Point2D.Double(nodeRealizer.getCenterX(), nodeRealizer.getCenterY())));
      });
    }
  }
}
