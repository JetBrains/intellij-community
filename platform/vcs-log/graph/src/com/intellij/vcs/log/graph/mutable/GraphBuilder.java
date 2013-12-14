package com.intellij.vcs.log.graph.mutable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Branch;
import com.intellij.vcs.log.graph.mutable.elements.MutableNode;
import com.intellij.vcs.log.graph.mutable.elements.MutableNodeRow;
import com.intellij.vcs.log.graph.mutable.elements.UsualEdge;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.graph.elements.Node.NodeType.*;

/**
 * @author erokhins
 */
public class GraphBuilder {

  private static final Logger LOG = Logger.getInstance(GraphBuilder.class);

  @NotNull
  public static MutableGraph build(@NotNull List<? extends GraphCommit> commitParentses, Collection<VcsRef> allRefs) {
    GraphBuilder builder = new GraphBuilder(commitParentses.size() - 1, calcCommitLogIndices(commitParentses), allRefs);
    return builder.runBuild(commitParentses);
  }

  @NotNull
  public static Map<Integer, Integer> calcCommitLogIndices(@NotNull List<? extends GraphCommit> commitParentses) {
    Map<Integer, Integer> commitLogIndexes = new HashMap<Integer, Integer>(commitParentses.size());
    for (int i = 0; i < commitParentses.size(); i++) {
      commitLogIndexes.put(commitParentses.get(i).getIndex(), i);
    }
    return commitLogIndexes;
  }

  // local package
  static void createUsualEdge(@NotNull MutableNode up, @NotNull MutableNode down, @NotNull Branch branch) {
    UsualEdge edge = new UsualEdge(up, down, branch);
    up.getInnerDownEdges().add(edge);
    down.getInnerUpEdges().add(edge);
  }

  private final int lastLogIndex;
  private final MutableGraph graph;
  private final Map<Integer, MutableNode> underdoneNodes;
  private Map<Integer, Integer> commitHashLogIndexes;
  private MultiMap<Integer, VcsRef> myRefsOfHashes;

  private MutableNodeRow nextRow;

  public GraphBuilder(int lastLogIndex,
                      Map<Integer, Integer> commitHashLogIndexes,
                      MutableGraph graph,
                      Map<Integer, MutableNode> underdoneNodes,
                      MutableNodeRow nextRow, Collection<VcsRef> refs) {
    this.lastLogIndex = lastLogIndex;
    this.commitHashLogIndexes = commitHashLogIndexes;
    this.graph = graph;
    this.underdoneNodes = underdoneNodes;
    this.nextRow = nextRow;

    myRefsOfHashes = prepareRefsMap(refs);
  }

  @NotNull
  private static MultiMap<Integer, VcsRef> prepareRefsMap(@NotNull Collection<VcsRef> refs) {
    MultiMap<Integer, VcsRef> map = MultiMap.create();
    for (VcsRef ref : refs) {
      map.putValue(ref.getCommitIndex(), ref);
    }
    return map;
  }

  public GraphBuilder(int lastLogIndex, Map<Integer, Integer> commitHashLogIndexes, MutableGraph graph, Collection<VcsRef> refs) {
    this(lastLogIndex, commitHashLogIndexes, graph, new HashMap<Integer, MutableNode>(), new MutableNodeRow(graph, 0), refs);
  }

  public GraphBuilder(int lastLogIndex, Map<Integer, Integer> commitHashLogIndexes, Collection<VcsRef> refs) {
    this(lastLogIndex, commitHashLogIndexes, new MutableGraph(), refs);
  }


  private int getLogIndexOfCommit(@NotNull Integer commitHash) {
    Integer index = commitHashLogIndexes.get(commitHash);
    if (index == null) {
      return lastLogIndex + 1;
    }
    else {
      return index;
    }
  }

  @NotNull
  private Collection<VcsRef> findRefForHash(int hash) {
    return myRefsOfHashes.get(hash);
  }

  private MutableNode addCurrentCommitAndFinishRow(int commitHash) {
    MutableNode node = underdoneNodes.remove(commitHash);
    if (node == null) {
      Collection<VcsRef> refs = findRefForHash(commitHash);
      node = createNode(commitHash, createBranch(commitHash, refs));
    }
    node.setType(COMMIT_NODE);
    node.setNodeRow(nextRow);

    nextRow.getInnerNodeList().add(node);
    graph.getAllRows().add(nextRow);
    nextRow = new MutableNodeRow(graph, nextRow.getRowIndex() + 1);
    return node;
  }

  @NotNull
  protected Branch createBranch(int commitHash, @NotNull Collection<VcsRef> refs) {
    VirtualFile repositoryRoot;
    if (refs.isEmpty()) {
      // should never happen, but fallback gently.
      LOG.error("Ref should exist for this node. Hash: " + commitHash);
      repositoryRoot = NullVirtualFile.INSTANCE;
    }
    else {
      repositoryRoot = refs.iterator().next().getRoot();
    }
    return new Branch(commitHash, refs, repositoryRoot);
  }

  private void addParent(MutableNode node, int parentHash, Branch branch) {
    MutableNode parentNode = underdoneNodes.remove(parentHash);
    if (parentNode == null) {
      parentNode = createNode(parentHash, branch);
      createUsualEdge(node, parentNode, branch);
      underdoneNodes.put(parentHash, parentNode);
    }
    else {
      createUsualEdge(node, parentNode, branch);
      int parentRowIndex = getLogIndexOfCommit(parentHash);

      // i.e. we need of create EDGE_NODE node
      if (nextRow.getRowIndex() != parentRowIndex) {
        parentNode.setNodeRow(nextRow);
        parentNode.setType(EDGE_NODE);
        nextRow.getInnerNodeList().add(parentNode);

        MutableNode newParentNode = createNode(parentHash, parentNode.getBranch());
        createUsualEdge(parentNode, newParentNode, parentNode.getBranch());
        underdoneNodes.put(parentHash, newParentNode);
      }
      else {
        // i.e. node must be added in nextRow, when addCurrentCommitAndFinishRow() will called in next time
        underdoneNodes.put(parentHash, parentNode);
      }
    }
  }

  private MutableNode createNode(int hash, Branch branch) {
    return new MutableNode(branch, hash);
  }

  private void append(@NotNull GraphCommit commit) {
    MutableNode node = addCurrentCommitAndFinishRow(commit.getIndex());

    int[] parents = commit.getParentIndices();
    Branch branch = node.getBranch();
    if (parents.length == 1) {
      addParent(node, parents[0], branch);
    }
    else {
      for (int parentHash : parents) {
        Collection<VcsRef> refs = findRefForHash(node.getCommitIndex());
        addParent(node, parentHash, new Branch(node.getCommitIndex(), parentHash, refs, branch.getRepositoryRoot()));
      }
    }
  }


  private void lastActions() {
    Set<Integer> notReadiedCommitHashes = underdoneNodes.keySet();
    for (Integer hash : notReadiedCommitHashes) {
      MutableNode underdoneNode = underdoneNodes.get(hash);
      underdoneNode.setNodeRow(nextRow);
      underdoneNode.setType(END_COMMIT_NODE);
      nextRow.getInnerNodeList().add(underdoneNode);
    }
    if (!nextRow.getInnerNodeList().isEmpty()) {
      graph.getAllRows().add(nextRow);
    }
  }

  // local package
  @NotNull
  public MutableGraph runBuild(@NotNull List<? extends GraphCommit> commitParentses) {
    for (GraphCommit vcsCommit : commitParentses) {
      append(vcsCommit);
    }
    lastActions();
    graph.updateVisibleRows();
    return graph;
  }

}
