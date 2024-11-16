package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.*;

public final class JourneyDiagramDataModel extends DiagramDataModel<JourneyNodeIdentity> {

  private final DiagramProvider<JourneyNodeIdentity> myProvider;
  private final Collection<JourneyNode> myNodes = new HashSet<>();
  private @NotNull List<JourneyEdge> myEdges = new ArrayList<>();
  public final JourneyEditorManager myEditorManager;

  public JourneyDiagramDataModel(@NotNull Project project,
                                 @NotNull DiagramProvider<JourneyNodeIdentity> provider) {
    this(project, provider, null);
  }

  public JourneyDiagramDataModel(@NotNull Project project,
                                 @NotNull DiagramProvider<JourneyNodeIdentity> provider,
                                 @Nullable DiagramNodeContentManager nodeContentManager) {
    super(project, provider, nodeContentManager);
    myProvider = provider;
    myEditorManager = new JourneyEditorManager();
    Disposer.register(this, myEditorManager);
  }

  public JourneyDiagramDataModel(@NotNull Project project,
                                    @NotNull DiagramProvider<JourneyNodeIdentity> provider,
                                    @NotNull DiagramVisibilityManager visibilityManager,
                                    @NotNull DiagramItemOrderingManager itemOrderingManager,
                                    @Nullable DiagramScopeManager<JourneyNodeIdentity> scopeManager,
                                    @Nullable DiagramNodeContentManager nodeContentManager) {
    super(project, provider, visibilityManager, itemOrderingManager, scopeManager, nodeContentManager);
    myProvider = provider;
    myEditorManager = new JourneyEditorManager();
    Disposer.register(this, myEditorManager);
  }

  @Override
  public @NotNull ModificationTracker getModificationTracker() {
    return ModificationTracker.NEVER_CHANGED;
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
    JourneyNode node = new JourneyNode(new JourneyNodeIdentity(psiElement), myProvider);
    myNodes.add(node);
    return node;
  }

  public void queryUpdate() {
    ApplicationManager.getApplication().invokeLater(() -> {
      getBuilder().queryUpdate()
        .withRelayout()
        .withAllNodeSizeUpdate()
        .withDataReload()
        .withNodePresentationsUpdate(true)
        .runAsync();
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
  }

  @Override
  public @Nullable DiagramEdge<JourneyNodeIdentity> createEdge(@NotNull DiagramNode<JourneyNodeIdentity> from,
                                                               @NotNull DiagramNode<JourneyNodeIdentity> to) {
    if (from.equals(to)) return null;
    JourneyEdge edge = new JourneyEdge(to, from, DiagramRelationships.DEPENDENCY);
    myEdges.add(edge);
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

  }
}
