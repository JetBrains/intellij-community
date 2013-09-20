package com.intellij.vcs.log.graph.mutable;

import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.mutable.elements.MutableNode;
import com.intellij.vcs.log.graph.mutable.elements.MutableNodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.graph.elements.Node.NodeType.*;

/**
 * @author erokhins
 */
//local package
class GraphAppendBuilder {


  private final MutableGraph graph;
  private final Collection<VcsRef> myRefs;

  public GraphAppendBuilder(MutableGraph graph, Collection<VcsRef> allRefs) {
    this.graph = graph;
    myRefs = allRefs;
  }

  private MutableNodeRow getLastRowInGraph() {
    List<MutableNodeRow> allRows = graph.getAllRows();
    assert !allRows.isEmpty() : "graph is empty!";
    return allRows.get(allRows.size() - 1);
  }

  private boolean isSimpleEndOfGraph() {
    List<MutableNodeRow> allRows = graph.getAllRows();
    assert !allRows.isEmpty() : "graph is empty!";
    MutableNodeRow lastRow = getLastRowInGraph();

    boolean hasCommitNode = false;
    for (MutableNode node : lastRow.getInnerNodeList()) {
      if (node.getType() == COMMIT_NODE) {
        hasCommitNode = true;
      }
    }
    if (hasCommitNode) {
      if (lastRow.getInnerNodeList().size() == 1) {
        return true;
      }
      else {
        throw new IllegalStateException("graph with commit node and more that 1 node in last row");
      }
    }
    else {
      return false;
    }
  }

  private Map<Hash, MutableNode> fixUnderdoneNodes(@NotNull Hash firstHash) {
    Map<Hash, MutableNode> underdoneNodes = new HashMap<Hash, MutableNode>();
    List<MutableNode> nodesInLaseRow = getLastRowInGraph().getInnerNodeList();
    MutableNode node;
    for (Iterator<MutableNode> iterator = nodesInLaseRow.iterator(); iterator.hasNext(); ) {
      node = iterator.next();

      if (node.getType() != END_COMMIT_NODE) {
        throw new IllegalStateException("bad last row in graph, unexpected node type: " + node.getType());
      }
      // i.e. it is EDGE_NODE
      if (node.getInnerUpEdges().size() > 1) {
        if (node.getCommitHash().equals(firstHash)) {
          iterator.remove();
          underdoneNodes.put(firstHash, node);
        }
        else {
          node.setType(EDGE_NODE);
          MutableNode newParentNode = new MutableNode(node.getBranch(), node.getCommitHash());
          GraphBuilder.createUsualEdge(node, newParentNode, node.getBranch());
          underdoneNodes.put(node.getCommitHash(), newParentNode);
        }
      }
      else {
        iterator.remove();
        underdoneNodes.put(node.getCommitHash(), node);
      }
    }

    return underdoneNodes;
  }

  private void simpleAppend(@NotNull List<? extends CommitParents> commitParentses,
                            @NotNull MutableNodeRow nextRow,
                            @NotNull Map<Hash, MutableNode> underdoneNodes) {
    int startIndex = nextRow.getRowIndex();

    Map<Hash, Integer> commitLogIndexes = new HashMap<Hash, Integer>(commitParentses.size());
    for (int i = 0; i < commitParentses.size(); i++) {
      commitLogIndexes.put(commitParentses.get(i).getHash(), i + startIndex);
    }

    GraphBuilder builder = new GraphBuilder(commitParentses.size() + startIndex - 1, commitLogIndexes, graph, underdoneNodes, nextRow,
                                            myRefs);
    builder.runBuild(commitParentses);
  }

  public void appendToGraph(@NotNull List<? extends CommitParents> commitParentses) {
    if (commitParentses.size() == 0) {
      throw new IllegalArgumentException("Empty list commitParentses");
    }
    if (isSimpleEndOfGraph()) {
      int startIndex = getLastRowInGraph().getRowIndex() + 1;
      simpleAppend(commitParentses, new MutableNodeRow(graph, startIndex), new HashMap<Hash, MutableNode>());
    }
    else {
      Map<Hash, MutableNode> underdoneNodes = fixUnderdoneNodes(commitParentses.get(0).getHash());
      MutableNodeRow lastRow = getLastRowInGraph();
      graph.getAllRows().remove(graph.getAllRows().size() - 1);
      simpleAppend(commitParentses, lastRow, underdoneNodes);
    }
  }
}
