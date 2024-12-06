package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.GraphManager;
import com.intellij.openapi.graph.geom.YPoint;
import com.intellij.openapi.graph.view.EdgeRealizer;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager;
import org.jetbrains.intellij.plugins.journey.util.JourneyNavigationUtil;

import java.util.*;
import java.util.List;

import static com.intellij.openapi.editor.ScrollType.CENTER_UP;
import static org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramLayout.getRealizer;
import static org.jetbrains.intellij.plugins.journey.util.PsiUtil.contains;
import static org.jetbrains.intellij.plugins.journey.util.PsiUtil.createSmartPointer;

public final class JourneyDiagramDataModel extends DiagramDataModel<JourneyNodeIdentity> {

  private final DiagramProvider<JourneyNodeIdentity> myProvider;
  private final HashSet<JourneyNode> myNodes = new HashSet<>();
  private final HashSet<JourneyEdge> myEdges = new HashSet<>();
  private SmartPsiElementPointer currentPSIatCaret = null;
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
  public @NotNull Set<JourneyNode> getNodes() {
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
    JourneyNode existingNode = getNode(identity);
    if (existingNode != null) {
      return existingNode;
    }

    JourneyNode newNode = new JourneyNode(myProvider, identity, title);
    myNodes.add(newNode);
    myModificationTracker.incModificationCount();
    queryUpdate(() -> {});
    return newNode;
  }

  @Override
  public void rebuild(JourneyNodeIdentity element) {
    refreshDataModel();
  }

  private void unionEdges(List<JourneyEdge> edgesFromNode, List<JourneyEdge> edgesToNode) {
    edgesToNode.forEach(inputEdge -> {
      SmartPsiElementPointer psiTarget = inputEdge.getPsiTarget();
      edgesFromNode.stream().filter(outputEdge -> {
        return contains(psiTarget, outputEdge.getPsiSource());
      }).forEach(outputEdge -> {
        createEdge(inputEdge.getPsiSource(), outputEdge.getPsiTarget());
      });
    });
  }

  @Override
  public void removeNode(@NotNull DiagramNode<JourneyNodeIdentity> node) {
    List<JourneyEdge> edgesToNode = findEdgesTo(node.getIdentifyingElement());
    List<JourneyEdge> edgesFromNode = findEdgesFrom(node.getIdentifyingElement());

    unionEdges(edgesFromNode, edgesToNode);
    myEdges.removeAll(edgesFromNode);
    myEdges.removeAll(edgesToNode);
    myEditorManager.closeNode(node.getIdentifyingElement());
    myNodes.removeIf(it -> Objects.equals(it, node));
    myModificationTracker.incModificationCount();
    queryUpdate(() -> {});
  }

  public void setCurrentPSIatCaret(SmartPsiElementPointer currentPSIatCaret) {
    this.currentPSIatCaret = currentPSIatCaret;
  }

  public @Nullable DiagramEdge<JourneyNodeIdentity> createEdge(SmartPsiElementPointer fromPSI,
                                                               SmartPsiElementPointer toPSI) {
    assert SmartPointerManager.getInstance(toPSI.getProject()) == SmartPointerManager.getInstance(fromPSI.getProject());

    JourneyEdge edge = new JourneyEdge(Objects.requireNonNull(getNode(new JourneyNodeIdentity(fromPSI))),
                                       Objects.requireNonNull(getNode(new JourneyNodeIdentity(toPSI))), fromPSI, toPSI);
    if (!myEdges.contains(edge)) {
      myEdges.add(edge);
      myModificationTracker.incModificationCount();
      queryUpdate(() -> {});
    }
    return edge;
  }

  @Override
  public void removeEdge(@NotNull DiagramEdge<JourneyNodeIdentity> edge) {
    boolean removed = myEdges.removeIf(it -> it.equals(edge));
    if (removed) {
      myModificationTracker.incModificationCount();
      queryUpdate(() -> {});
    }
  }

  @Override
  public @NotNull Set<JourneyEdge> getEdges() {
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
    var node = Optional.of(addElement(new JourneyNodeIdentity(createSmartPointer(element))));
    queryUpdate(() -> {
      JourneyDiagramLayout.addElementLayout(getBuilder(), node.get());
    });
  }

  public void highlightEdges() {
    for (JourneyEdge edge : myEdges) {
      Optional<EdgeRealizer> realizer = getRealizer(this.getBuilder(), edge);
      if (edge.getSource().getIdentifyingElement().equals(edge.getTarget().getIdentifyingElement())) {
        realizer.get().setVisible(false);
      }
      if (realizer.isPresent()) {
        NodeRealizer source = getRealizer(getBuilder(), (JourneyNode)edge.getSource()).get();
        NodeRealizer target = getRealizer(getBuilder(), (JourneyNode)edge.getTarget()).get();
        YPoint point;
        boolean isIntersect = ContainerUtil.exists(myNodes, node -> {
          if (!edge.getSource().equals(node) && !edge.getTarget().equals(node)) {
            return realizer.get().intersects(getRealizer(getBuilder(), node).get().getBoundingBox());
          }
          return false;
        });
        final JourneyNode sourceNode = (JourneyNode)edge.getSource();
        final JourneyNode targetNode = (JourneyNode)edge.getTarget();
        if (!isIntersect && (source.getX() + source.getWidth() < target.getX() || target.getX() + target.getWidth() < source.getX())) {
          boolean isLeftToRight = source.getX() + source.getWidth() < target.getX();
          realizer.get().clearPoints();
          point = GraphManager.getGraphManager().createYPoint((isLeftToRight ? 1 : -1) * source.getWidth() / 2.0,
                                                              sourceNode.getRealizerCoord(edge.getPsiSource()));
          realizer.get().setSourcePoint(point);
          point = GraphManager.getGraphManager().createYPoint((isLeftToRight ? -1 : 1) * target.getWidth() / 2.0,
                                                              targetNode.getRealizerCoord(edge.getPsiTarget()));
          realizer.get().setTargetPoint(point);
        } else {
          point = GraphManager.getGraphManager().createYPoint(realizer.get().getSourcePoint().getX(),
                                                              sourceNode.getRealizerCoord(edge.getPsiSource()));
          realizer.get().setSourcePoint(point);
          point = GraphManager.getGraphManager().createYPoint(realizer.get().getTargetPoint().getX(),
                                                              targetNode.getRealizerCoord(edge.getPsiTarget()));
          realizer.get().setTargetPoint(point);
        }
        realizer.get().setDirty();

        if (currentPSIatCaret != null && (contains(currentPSIatCaret, edge.getPsiSource()) ||
                                          contains(currentPSIatCaret, edge.getPsiTarget()))) {
          realizer.get().setLineColor(JBColor.GREEN);
        } else {
          realizer.get().setLineColor(JBColor.GRAY);
        }
      }
    }
  }

  public void queryUpdate(Runnable thenRun) {
    ApplicationManager.getApplication().invokeLater(() -> {
      getBuilder().queryUpdate()
        .withDataReload()
        .withNodePresentationsUpdate(true)
        .runAsync()
        .thenRun(thenRun)
        .thenRun(() -> {
          updateHighlight();
        });
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

  private List<SmartPsiElementPointer> getPsiElementsForNode(JourneyNode node) {
    Set<SmartPsiElementPointer> psiElements = new HashSet<>() ;
    myEdges.forEach((edge) -> {
      if (edge.getSource().equals(node)) {
        psiElements.add(edge.getPsiSource());
      }
      if (edge.getTarget().equals(node)) {
        psiElements.add(edge.getPsiTarget());
      }
    });
    psiElements.add(node.getIdentifyingElement().getIdentifierElement());
    return new ArrayList<>(psiElements);
  }

  public void highlightNode(JourneyNode node) {
    ReadAction.run(() -> {
      node.highlightMembers(getPsiElementsForNode(node));
    });
  }

  private void updateHighlight() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      myNodes.forEach(it -> {
        highlightNode(it);
      });
    });
  }

  public void addEdge(JourneyNode from, JourneyNode to, String relation) {
    JourneyEdge edge = new JourneyEdge(from, to, relation);
    if (!myEdges.contains(edge)) {
      myEdges.add(edge);
      myModificationTracker.incModificationCount();
      queryUpdate(() -> {});
    }
  }

  private void addEdge(Object from, Object to) {
    PsiElement fromPSI = JourneyNavigationUtil.findPsiElement(getProject(), from);
    PsiElement toPSI = JourneyNavigationUtil.findPsiElement(getProject(), to);
    if (fromPSI == null || toPSI == null) {
      // TODO add info to logger
      return;
    }

    var smartFromPSI = createSmartPointer(fromPSI);
    var smartToPSI = createSmartPointer(toPSI);
    var fromNode = addElement(new JourneyNodeIdentity(smartFromPSI));
    var toNode = addElement(new JourneyNodeIdentity(smartToPSI));
    createEdge(smartFromPSI, smartToPSI);
    boolean isNewNode = !isNodeExist(fromNode) || !isNodeExist(toNode);
    if (isNewNode) {
      boolean isLeftToRight = isNodeExist(fromNode);
      addNewEdgeElementUpdate(fromNode, toNode, isLeftToRight);
    } else {
      queryUpdate(() -> {});
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (!myEditorManager.NODE_PANELS.containsKey(fromNode.getIdentifyingElement().getFile())) {
        return;
      }

      Editor editor = myEditorManager.NODE_PANELS.get(fromNode.getIdentifyingElement().getFile()).getEditor();
      var navigateNode = toNode;
      var navigatePSI = toPSI;
      if (!(editor != null && IdeFocusManager.getInstance(editor.getProject()).getFocusedDescendantFor(editor.getComponent()) != null &&
           fromPSI.getTextRange().contains(editor.getCaretModel().getOffset()))) {
        navigateNode = fromNode;
        navigatePSI = fromPSI;
      }
      if (myEditorManager.NODE_PANELS.containsKey(navigateNode.getIdentifyingElement().getFile())) {
        editor = myEditorManager.NODE_PANELS.get(navigateNode.getIdentifyingElement().getFile()).getEditor();
        if (editor != null) {
          editor.getCaretModel().moveToOffset(navigatePSI.getTextRange().getStartOffset());
          editor.getScrollingModel().scrollToCaret(CENTER_UP);
        }
      }

      if (!fromNode.equals(toNode)) {
        JourneyNode finalNavigateNode = navigateNode;
        new Alarm().addRequest(() -> {
          finalNavigateNode.navigate(false);
        }, 500);
      }
    });
  }
}
