package org.jetbrains.intellij.plugins.journey.diagram.persistence;

import com.intellij.diagram.DiagramVfsResolver;
import com.intellij.diagram.presentation.DiagramState;
import com.intellij.diagram.presentation.EdgeInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNode;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNodeIdentity;

import java.util.HashMap;
import java.util.Map;

public class JourneyUmlFileSnapshotLoader {
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
    DiagramState diagramState = readDiagramState(file);
    Map<String, JourneyNodeIdentity> cache = new HashMap<>();
    Map<String, JourneyNode> cache2 = new HashMap<>();
    for (String fqn : diagramState.getFQNs()) {
      JourneyNode node = dataModel.addElement(cache.computeIfAbsent(fqn, __ -> myVfsResolver.resolveElementByFQN(fqn, myProject)));
      cache2.put(fqn, node);
    }
    for (EdgeInfo edge : diagramState.getEdgeInfos()) {
      JourneyNode from = cache2.get(edge.getSrc());
      JourneyNode to = cache2.get(edge.getTrg());
      if (from != null && to != null) {
        dataModel.createEdge(from, to);
      }
    }
  }

  private static DiagramState readDiagramState(@Nullable VirtualFile file) {
    if (file == null) return null;
    DiagramState diagramState;
    try {
      diagramState = DiagramState.read(file.getInputStream());
    }
    catch (Exception e) {
      throw new RuntimeException("Could not load diagram state from .uml file", e);
    }
    return diagramState;
  }
}
