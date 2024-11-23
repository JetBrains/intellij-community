package org.jetbrains.intellij.plugins.journey.diagram;// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.diagram.*;
import com.intellij.diagram.extras.DiagramExtras;
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
import com.intellij.uml.presentation.DiagramPresentationModelImpl;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.diagram.persistence.JourneyUmlFileSnapshotLoader;

import javax.swing.*;
import java.util.Collection;
import java.util.Optional;

import static org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager.updateEditorSize;

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
        dataModel.queryUpdate(() -> {});
      } catch (Exception e) {
        LOG.error("Could not load snapshot from .uml file", e);
      }

      return dataModel;
    }
    catch (Exception e) {
      Messages.showErrorDialog(project, "Could not create data model: " + e.getMessage(), "Journey Diagrams");
      return new JourneyDiagramDataModel(project, this);
    }
  }

  public static void addEdge(PsiElement from, PsiElement to, JourneyDiagramDataModel model) {
    var fromNode = Optional.ofNullable(findNodeForFile(model, from)).orElseGet(() -> model.addElement(new JourneyNodeIdentity(from)));
    var toNode = Optional.ofNullable(findNodeForFile(model, to)).orElseGet(() -> model.addElement(new JourneyNodeIdentity(to)));
    if (toNode == null || fromNode == null) {
      return;
    }

    boolean isLeftToRight = model.isNodeExist(fromNode);
    model.createEdge(fromNode, toNode);
    model.addNewPairUpdate(fromNode, toNode, isLeftToRight);
  }

  private static @Nullable JourneyNode findNodeForFile(JourneyDiagramDataModel model, PsiElement node) {
    return ContainerUtil.find(model.getNodes(), e ->
      e.getIdentifyingElement().element().isEquivalentTo(node));
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
          if (builder.getGraphDataModel() instanceof DiagramDataModelWrapper ddmw
              && ddmw.getModel() instanceof JourneyDiagramDataModel journeyDiagramDataModel
          ) {
            Collection<PsiElement> elements = journeyDiagramDataModel.myEditorManager.OPENED_JOURNEY_EDITORS.keySet();
            for (PsiElement psi : elements) {
              Editor editor = journeyDiagramDataModel.myEditorManager.OPENED_JOURNEY_EDITORS.get(psi);
              // TODO such revalidation cause to flickering
              SwingUtilities.invokeLater(() -> {
                updateEditorSize(editor, JourneyNodeCellRenderer.getRealizer(psi), psi, (float)builder.getZoom());
                editor.getComponent().revalidate();
              });
            }
          }
        }
      }
    };
  }
}
