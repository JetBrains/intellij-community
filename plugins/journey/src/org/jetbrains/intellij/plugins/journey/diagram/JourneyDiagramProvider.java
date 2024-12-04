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
import com.intellij.util.containers.DisposableWrapperList;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.intellij.plugins.journey.diagram.persistence.JourneyUmlFileSnapshotLoader;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.intellij.plugins.journey.editor.JourneyEditorManager.BASE_FONT_SIZE;

@SuppressWarnings("HardCodedStringLiteral")
public final class JourneyDiagramProvider extends BaseDiagramProvider<JourneyNodeIdentity> {
  public static final String ID = "JOURNEY";
  private static final Logger LOG = Logger.getInstance(JourneyDiagramProvider.class);

  /**
   For some reason {@link com.intellij.uml.UmlEditorProvider} (private method #getProviderFromFile) COPIES provider via ctor,
   so models become diverged.
   Therefore they are static for now.
   TODO Make them non-static when Journey uses custom EditorProvider
  */
  private static final List<JourneyDiagramDataModel> myModels = new DisposableWrapperList<>();

  public static @NotNull JourneyDiagramProvider getInstance() {
    return DIAGRAM_PROVIDER.getExtensionList().stream()
      .filter(it -> it instanceof JourneyDiagramProvider)
      .map(it -> (JourneyDiagramProvider)it)
      .findAny().orElseThrow();
  }

  public @Unmodifiable @NotNull List<JourneyDiagramDataModel> getModels() {
    return Collections.unmodifiableList(myModels);
  }

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

      myModels.add(dataModel);
      return dataModel;
    }
    catch (Exception e) {
      Messages.showErrorDialog(project, "Could not create data model: " + e.getMessage(), "Journey Diagrams");
      return new JourneyDiagramDataModel(project, this);
    }
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
            journeyDiagramDataModel.myEditorManager.revalidateEditors((float)builder.getZoom());
          }
        }
      }
    };
  }
}
