package org.jetbrains.intellij.plugins.journey.diagram;// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.diagram.*;
import com.intellij.diagram.extras.DiagramExtras;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.graph.builder.GraphBuilder;
import com.intellij.openapi.graph.builder.event.GraphBuilderEvent;
import com.intellij.openapi.graph.view.Graph2D;
import com.intellij.openapi.graph.view.NodeCellRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.uml.presentation.DiagramPresentationModelImpl;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.diagram.persistence.JourneyUmlFileSnapshotLoader;
import org.jetbrains.intellij.plugins.journey.navigation.JourneyNavigationUtils;

import javax.swing.*;
import java.util.Collection;
import java.util.Optional;

@SuppressWarnings("HardCodedStringLiteral")
public final class JourneyDiagramProvider extends BaseDiagramProvider<JourneyNodeIdentity> {
  public static final String ID = "JOURNEY";
  private static final Logger LOG = Logger.getInstance(JourneyDiagramProvider.class);

  private final DiagramElementManager<JourneyNodeIdentity> myElementManager;
  private final JourneyDiagramVfsResolver myVfsResolver = new JourneyDiagramVfsResolver();
  private final JourneyDiagramExtras myExtras = new JourneyDiagramExtras();

  public JourneyDiagramProvider() {
    myElementManager = new JourneyDiagramElementManager();
    myElementManager.setUmlProvider(this);
  }

  @Override
  @Pattern("[a-zA-Z0-9_-]*")
  public @NotNull String getID() {
    return ID;
  }

  @Override
  public @NotNull String getPresentableName() {
    return ID + " presentable name.";
  }

  @Override
  public @NotNull DiagramDataModel<JourneyNodeIdentity> createDataModel(
    @NotNull Project project,
    @Nullable JourneyNodeIdentity element,
    @Nullable VirtualFile file,
    @NotNull DiagramPresentationModel presentationModel
  ) {
    try {
      var dataModel = new JourneyDiagramDataModel(project, this);

      if (element != null) {
        dataModel.addElement(element);
      }

      JourneyUmlFileSnapshotLoader loader = new JourneyUmlFileSnapshotLoader(project, myVfsResolver);
      try {
        loader.load(file, dataModel);
      } catch (Exception e) {
        LOG.error("Could not load snapshot from .uml file", e);
      }

      project.putUserData(Project.JOURNEY_NAVIGATION_INTERCEPTOR, (element1, element2) -> navigate(project, dataModel, element1, element2));

      return dataModel;
    }
    catch (Exception e) {
      Messages.showErrorDialog(project, "Could not create data model: " + e.getMessage(), "Journey Diagrams");
      return new JourneyDiagramDataModel(project, this);
    }
  }

  private static Boolean navigate(Project project, JourneyDiagramDataModel model, Object from, Object to) {
    PsiElement fromResult = JourneyNavigationUtils.findPsiElement(project, from);
    PsiElement toResult = JourneyNavigationUtils.findPsiElement(project, to);
    addEdge(fromResult, toResult, model);
    return true;
  }

  public static void addEdge(PsiElement from, PsiElement to, JourneyDiagramDataModel model) {
    var fromNode = Optional.ofNullable(findNodeForFile(model, from)).orElseGet(() -> model.addElement(new JourneyNodeIdentity(from)));
    var toNode = Optional.ofNullable(findNodeForFile(model, to)).orElseGet(() -> model.addElement(new JourneyNodeIdentity(to)));
    if (toNode == null || fromNode == null) {
      return;
    }

    model.createEdge(fromNode, toNode);
    model.queryUpdate();
  }

  private static @Nullable JourneyNode findNodeForFile(JourneyDiagramDataModel model, PsiElement from) {
    if (from == null) return null;
    PsiFile fromFile = ReadAction.nonBlocking(() -> from.getContainingFile()).executeSynchronously();
    return ContainerUtil.find(model.getNodes(), node -> {
      PsiElement element = node.getIdentifyingElement().element();
      PsiFile toFile = ReadAction.nonBlocking(() -> element.getContainingFile()).executeSynchronously();
      return toFile.isEquivalentTo(fromFile);
    });
  }

  @Override
  public @NotNull DiagramElementManager<JourneyNodeIdentity> getElementManager() {
    return myElementManager;
  }

  @Override
  public @NotNull DiagramVfsResolver<JourneyNodeIdentity> getVfsResolver() {
    return myVfsResolver;
  }

  @Override
  public @NotNull DiagramExtras<JourneyNodeIdentity> getExtras() {
    return myExtras;
  }

  @Override
  public @Nullable DiagramPresentationModel createPresentationModel(@NotNull Project project, @NotNull Graph2D graph) {
    return new DiagramPresentationModelImpl(graph, project, this) {
      @Override
      protected @NotNull NodeCellRenderer createRenderer() {
        return createRenderer(getBuilder());
      }

      @Override
      protected @NotNull NodeCellRenderer createRenderer(@NotNull DiagramBuilder builder) {
        return new JourneyNodeCellRenderer(builder, getModificationTrackerOfViewUpdates());
      }

      @Override
      public void actionPerformed(@NotNull GraphBuilder<?, ?> builder, @NotNull GraphBuilderEvent event) {
        super.actionPerformed(builder, event);
        if (event == GraphBuilderEvent.ZOOM_CHANGED) {
          int font = (int)(13 * builder.getZoom());
          font = Math.min(font, 13);
          if (builder.getGraphDataModel() instanceof DiagramDataModelWrapper ddmw
              && ddmw.getModel() instanceof JourneyDiagramDataModel journeyDiagramDataModel
          ) {
            Collection<Editor> editors = journeyDiagramDataModel.myEditorManager.OPENED_JOURNEY_EDITORS.values();
            for (Editor editor : editors) {
              editor.getColorsScheme().setEditorFontSize(font);
              // TODO such revalidation cause to flickering 
              SwingUtilities.invokeLater(() -> editor.getComponent().revalidate());
            }
          }
        }
      }
    };
  }
}
