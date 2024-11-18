package org.jetbrains.intellij.plugins.journey.diagram.persistence;

import com.intellij.diagram.DiagramVfsResolver;
import com.intellij.diagram.presentation.DiagramState;
import com.intellij.diagram.presentation.EdgeInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNode;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNodeIdentity;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class JourneyUmlFileSnapshotLoader {
  private static final Logger LOG = Logger.getInstance(JourneyUmlFileSnapshotLoader.class);

  private final @NotNull Project myProject;
  private final @NotNull DiagramVfsResolver<JourneyNodeIdentity> myVfsResolver;

  public JourneyUmlFileSnapshotLoader(
    @NotNull Project project,
    @NotNull DiagramVfsResolver<JourneyNodeIdentity> vfsResolver
  ) {
    myProject = project;
    myVfsResolver = vfsResolver;
  }

  public void load(
    VirtualFile file,
    JourneyDiagramDataModel dataModel
  ) {
    DiagramState diagramState = tryReadDiagramState(file);
    if (diagramState == null) return;
    Map<String, JourneyNodeIdentity> cache = new HashMap<>();
    Map<String, JourneyNode> cache2 = new HashMap<>();
    for (String fqn : diagramState.getFQNs()) {
      JourneyNodeIdentity identity = myVfsResolver.resolveElementByFQN(fqn, myProject);
      if (identity == null) {
        LOG.warn("Could not resolve element by FQN: " + fqn);
      }
      JourneyNodeIdentity element = cache.computeIfAbsent(fqn, __ -> identity);
      if (element != null) {
        JourneyNode node = dataModel.addElement(element);
        cache2.put(fqn, node);
      }
    }
    for (EdgeInfo edge : diagramState.getEdgeInfos()) {
      JourneyNode from = cache2.get(edge.getSrc());
      JourneyNode to = cache2.get(edge.getTrg());
      if (from != null && to != null) {
        dataModel.createEdge(from, to);
      }
    }
  }

  private static @Nullable DiagramState tryReadDiagramState(@Nullable VirtualFile file) {
    if (file == null) return null;
    DiagramState diagramState;
    InputStream stream;
    try {
      stream = file.getInputStream();
    }
    catch (Exception e) {
      LOG.warn("Could not read .uml file: " + file.getCanonicalPath() + ", Has no input stream", e);
      return null;
    }
    try {
      diagramState = DiagramState.read(stream);
    }
    catch (Exception e) {
      throw new RuntimeException("Could not load diagram state from .uml file " + file, e);
    }
    return diagramState;
  }
}
