/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNodeType;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAction;
import com.intellij.vcs.log.graph.impl.print.PrintElementGeneratorImpl;
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getCursor;

public class VisibleGraphImpl<CommitId> implements VisibleGraph<CommitId> {
  @NotNull private final LinearGraphController myGraphController;
  @NotNull private final PermanentGraphInfo<CommitId> myPermanentGraph;
  @NotNull private final GraphColorManager<CommitId> myColorManager;

  private PrintElementManagerImpl myPrintElementManager;
  private PrintElementGeneratorImpl myPrintElementGenerator;
  private boolean myShowLongEdges = false;

  public VisibleGraphImpl(@NotNull LinearGraphController graphController,
                          @NotNull PermanentGraphInfo<CommitId> permanentGraph,
                          @NotNull GraphColorManager<CommitId> colorManager) {
    myGraphController = graphController;
    myPermanentGraph = permanentGraph;
    myColorManager = colorManager;
    updatePrintElementGenerator();
  }

  @Override
  public int getVisibleCommitCount() {
    return myGraphController.getCompiledGraph().nodesCount();
  }

  @NotNull
  @Override
  public RowInfo<CommitId> getRowInfo(final int visibleRow) {
    final int nodeId = getNodeId(visibleRow);
    assert nodeId >= 0; // todo remake for all id
    return new RowInfoImpl(nodeId, visibleRow);
  }

  public int getNodeId(int visibleRow) {
    return myGraphController.getCompiledGraph().getNodeId(visibleRow);
  }

  @Override
  @Nullable
  public Integer getVisibleRowIndex(@NotNull CommitId commitId) {
    int nodeId = myPermanentGraph.getPermanentCommitsInfo().getNodeId(commitId);
    return myGraphController.getCompiledGraph().getNodeIndex(nodeId);
  }

  @NotNull
  @Override
  public ActionController<CommitId> getActionController() {
    return new ActionControllerImpl();
  }

  private void updatePrintElementGenerator() {
    myPrintElementManager = new PrintElementManagerImpl(myGraphController.getCompiledGraph(), myPermanentGraph, myColorManager);
    myPrintElementGenerator = new PrintElementGeneratorImpl(myGraphController.getCompiledGraph(), myPrintElementManager, myShowLongEdges);
  }

  @NotNull
  public SimpleGraphInfo<CommitId> buildSimpleGraphInfo() {
    return SimpleGraphInfo.build(myGraphController.getCompiledGraph(),
                                 myPermanentGraph.getPermanentGraphLayout(),
                                 myPermanentGraph.getPermanentCommitsInfo(),
                                 myPermanentGraph.getLinearGraph().nodesCount(),
                                 myPermanentGraph.getBranchNodeIds());
  }

  public int getRecommendedWidth() {
    return myPrintElementGenerator.getRecommendedWidth();
  }

  public LinearGraph getLinearGraph() {
    return myGraphController.getCompiledGraph();
  }

  @NotNull
  public PermanentGraphInfo<CommitId> getPermanentGraph() {
    return myPermanentGraph;
  }

  private class ActionControllerImpl implements ActionController<CommitId> {

    @Nullable
    private Integer convertToNodeId(@Nullable Integer nodeIndex) {
      if (nodeIndex == null) return null;
      return getNodeId(nodeIndex);
    }

    @Nullable
    private GraphAnswer<CommitId> performArrowAction(@NotNull LinearGraphAction action) {
      PrintElementWithGraphElement affectedElement = action.getAffectedElement();
      if (!(affectedElement instanceof EdgePrintElement)) return null;
      EdgePrintElement edgePrintElement = (EdgePrintElement)affectedElement;
      if (!edgePrintElement.hasArrow()) return null;

      GraphElement graphElement = affectedElement.getGraphElement();
      if (!(graphElement instanceof GraphEdge)) return null;
      GraphEdge edge = (GraphEdge)graphElement;

      Integer targetId = null;
      if (edge.getType() == GraphEdgeType.NOT_LOAD_COMMIT) {
        assert edgePrintElement.getType().equals(EdgePrintElement.Type.DOWN);
        targetId = edge.getTargetId();
      }
      if (edge.getType().isNormalEdge()) {
        if (edgePrintElement.getType().equals(EdgePrintElement.Type.DOWN)) {
          targetId = convertToNodeId(edge.getDownNodeIndex());
        }
        else {
          targetId = convertToNodeId(edge.getUpNodeIndex());
        }
      }
      if (targetId == null) return null;

      if (action.getType() == GraphAction.Type.MOUSE_OVER) {
        myPrintElementManager.setSelectedElement(affectedElement);
        return new GraphAnswerImpl<>(getCursor(true), myPermanentGraph.getPermanentCommitsInfo().getCommitId(targetId), null,
                                     false);
      }

      if (action.getType() == GraphAction.Type.MOUSE_CLICK) {
        return new GraphAnswerImpl<>(getCursor(false), myPermanentGraph.getPermanentCommitsInfo().getCommitId(targetId), null,
                                     true);
      }

      return null;
    }

    @NotNull
    @Override
    public GraphAnswer<CommitId> performAction(@NotNull GraphAction graphAction) {
      myPrintElementManager.setSelectedElements(Collections.emptySet());

      LinearGraphAction action = convert(graphAction);
      GraphAnswer<CommitId> graphAnswer = performArrowAction(action);
      if (graphAnswer != null) return graphAnswer;

      LinearGraphController.LinearGraphAnswer answer = myGraphController.performLinearGraphAction(action);
      if (answer.getSelectedNodeIds() != null) myPrintElementManager.setSelectedElements(answer.getSelectedNodeIds());

      if (answer.getGraphChanges() != null) updatePrintElementGenerator();
      return convert(answer);
    }

    @Override
    public boolean areLongEdgesHidden() {
      return !myShowLongEdges;
    }

    @Override
    public void setLongEdgesHidden(boolean longEdgesHidden) {
      myShowLongEdges = !longEdgesHidden;
      updatePrintElementGenerator();
    }

    @NotNull
    private LinearGraphAction convert(@NotNull GraphAction graphAction) {
      PrintElementWithGraphElement printElement = null;
      PrintElement affectedElement = graphAction.getAffectedElement();
      if (affectedElement != null) {
        if (affectedElement instanceof PrintElementWithGraphElement) {
          printElement = (PrintElementWithGraphElement)affectedElement;
        } else {
          printElement = ContainerUtil.find(myPrintElementGenerator.getPrintElements(affectedElement.getRowIndex()), it -> it.equals(affectedElement));
          if (printElement == null) {
            throw new IllegalStateException("Not found graphElement for this printElement: " + affectedElement);
          }
        }
      }
      return new LinearGraphActionImpl(printElement, graphAction.getType());
    }

    private GraphAnswer<CommitId> convert(@NotNull final LinearGraphController.LinearGraphAnswer answer) {
      final Runnable graphUpdater = answer.getGraphUpdater();
      return new GraphAnswerImpl<>(answer.getCursorToSet(), null, graphUpdater == null ? null : (Runnable)() -> {
        graphUpdater.run();
        updatePrintElementGenerator();
      }, false);
    }
  }

  private static class GraphAnswerImpl<CommitId> implements GraphAnswer<CommitId> {
    @Nullable private final Cursor myCursor;
    @Nullable private final CommitId myCommitToJump;
    @Nullable private final Runnable myUpdater;
    private final boolean myDoJump;

    private GraphAnswerImpl(@Nullable Cursor cursor, @Nullable CommitId commitToJump, @Nullable Runnable updater, boolean doJump) {
      myCursor = cursor;
      myCommitToJump = commitToJump;
      myUpdater = updater;
      myDoJump = doJump;
    }

    @Nullable
    @Override
    public Cursor getCursorToSet() {
      return myCursor;
    }

    @Nullable
    @Override
    public CommitId getCommitToJump() {
      return myCommitToJump;
    }

    @Nullable
    @Override
    public Runnable getGraphUpdater() {
      return myUpdater;
    }

    @Override
    public boolean doJump() {
      return myDoJump;
    }
  }

  public static class LinearGraphActionImpl implements LinearGraphAction {
    @Nullable private final PrintElementWithGraphElement myAffectedElement;
    @NotNull private final Type myType;

    public LinearGraphActionImpl(@Nullable PrintElementWithGraphElement affectedElement, @NotNull Type type) {
      myAffectedElement = affectedElement;
      myType = type;
    }

    @Nullable
    @Override
    public PrintElementWithGraphElement getAffectedElement() {
      return myAffectedElement;
    }

    @NotNull
    @Override
    public Type getType() {
      return myType;
    }
  }

  private class RowInfoImpl implements RowInfo<CommitId> {
    private final int myNodeId;
    private final int myVisibleRow;

    public RowInfoImpl(int nodeId, int visibleRow) {
      myNodeId = nodeId;
      myVisibleRow = visibleRow;
    }

    @NotNull
    @Override
    public CommitId getCommit() {
      return myPermanentGraph.getPermanentCommitsInfo().getCommitId(myNodeId);
    }

    @NotNull
    @Override
    public CommitId getOneOfHeads() {
      int headNodeId = myPermanentGraph.getPermanentGraphLayout().getOneOfHeadNodeIndex(myNodeId);
      return myPermanentGraph.getPermanentCommitsInfo().getCommitId(headNodeId);
    }

    @NotNull
    @Override
    public Collection<? extends PrintElement> getPrintElements() {
      return myPrintElementGenerator.getPrintElements(myVisibleRow);
    }

    @NotNull
    @Override
    public RowType getRowType() {
      GraphNodeType nodeType = myGraphController.getCompiledGraph().getGraphNode(myVisibleRow).getType();
      switch (nodeType) {
        case USUAL:
          return RowType.NORMAL;
        case UNMATCHED:
          return RowType.UNMATCHED;
        default:
          throw new UnsupportedOperationException("Unsupported node type: " + nodeType);
      }
    }
  }
}
