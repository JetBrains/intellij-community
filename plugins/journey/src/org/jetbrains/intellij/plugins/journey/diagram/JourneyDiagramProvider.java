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
import com.intellij.uml.presentation.DiagramPresentationModelImpl;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.diagram.persistence.JourneyUmlFileSnapshotLoader;

import javax.swing.*;
import java.util.Collection;

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

      project.putUserData(Project.JOURNEY_NAVIGATION_INTERCEPTOR, (element1, element2) -> navigate(dataModel, element1, element2));

      return dataModel;
    }
    catch (Exception e) {
      Messages.showErrorDialog(project, "Could not create data model: " + e.getMessage(), "Journey Diagrams");
      return new JourneyDiagramDataModel(project, this);
    }
  }

  public static Boolean navigate(JourneyDiagramDataModel model, Object from, Object to) {
    model.addEdge(from, to);
    return true;
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
