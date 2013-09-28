package com.intellij.vcs.log.graph.mutable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommit;
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

  public static MutableGraph build(@NotNull List<? extends VcsCommit> commitParentses, Collection<VcsRef> allRefs) {
    Map<Hash, Integer> commitLogIndexes = new HashMap<Hash, Integer>(commitParentses.size());
    for (int i = 0; i < commitParentses.size(); i++) {
      commitLogIndexes.put(commitParentses.get(i).getHash(), i);
    }
    GraphBuilder builder = new GraphBuilder(commitParentses.size() - 1, commitLogIndexes, allRefs);
    return builder.runBuild(commitParentses);
  }

  public static void addCommitsToGraph(@NotNull MutableGraph graph, @NotNull List<? extends VcsCommit> commitParentses,
                                       @NotNull Collection<VcsRef> allRefs) {
    new GraphAppendBuilder(graph, allRefs).appendToGraph(commitParentses);
  }

  // local package
  static void createUsualEdge(@NotNull MutableNode up, @NotNull MutableNode down, @NotNull Branch branch) {
    UsualEdge edge = new UsualEdge(up, down, branch);
    up.getInnerDownEdges().add(edge);
    down.getInnerUpEdges().add(edge);
  }

  private final int lastLogIndex;
  private final MutableGraph graph;
  private final Map<Hash, MutableNode> underdoneNodes;
  private Map<Hash, Integer> commitHashLogIndexes;
  private MultiMap<Hash, VcsRef> myRefsOfHashes;

  private MutableNodeRow nextRow;

  public GraphBuilder(int lastLogIndex,
                      Map<Hash, Integer> commitHashLogIndexes,
                      MutableGraph graph,
                      Map<Hash, MutableNode> underdoneNodes,
                      MutableNodeRow nextRow, Collection<VcsRef> refs) {
    this.lastLogIndex = lastLogIndex;
    this.commitHashLogIndexes = commitHashLogIndexes;
    this.graph = graph;
    this.underdoneNodes = underdoneNodes;
    this.nextRow = nextRow;

    myRefsOfHashes = prepareRefsMap(refs);
  }

  @NotNull
  private static MultiMap<Hash, VcsRef> prepareRefsMap(@NotNull Collection<VcsRef> refs) {
    MultiMap<Hash, VcsRef> map = MultiMap.create();
    for (VcsRef ref : refs) {
      map.putValue(ref.getCommitHash(), ref);
    }
    return map;
  }

  public GraphBuilder(int lastLogIndex, Map<Hash, Integer> commitHashLogIndexes, MutableGraph graph, Collection<VcsRef> refs) {
    this(lastLogIndex, commitHashLogIndexes, graph, new HashMap<Hash, MutableNode>(), new MutableNodeRow(graph, 0), refs);
  }

  public GraphBuilder(int lastLogIndex, Map<Hash, Integer> commitHashLogIndexes, Collection<VcsRef> refs) {
    this(lastLogIndex, commitHashLogIndexes, new MutableGraph(), refs);
  }


  private int getLogIndexOfCommit(@NotNull Hash commitHash) {
    Integer index = commitHashLogIndexes.get(commitHash);
    if (index == null) {
      return lastLogIndex + 1;
    }
    else {
      return index;
    }
  }

  @NotNull
  private Collection<VcsRef> findRefForHash(@NotNull final Hash hash) {
    return myRefsOfHashes.get(hash);
  }

  private MutableNode addCurrentCommitAndFinishRow(@NotNull Hash commitHash) {
    MutableNode node = underdoneNodes.remove(commitHash);
    if (node == null) {
      Collection<VcsRef> refs = findRefForHash(commitHash);
      VirtualFile repositoryRoot;
      if (refs.isEmpty()) {
        // should never happen, but fallback gently.
        LOG.error("Ref should exist for this node. Hash: " + commitHash);
        repositoryRoot = NullVirtualFile.INSTANCE;
      }
      else {
        repositoryRoot = refs.iterator().next().getRoot();
      }
      node = createNode(commitHash, new Branch(commitHash, refs, repositoryRoot));
    }
    node.setType(COMMIT_NODE);
    node.setNodeRow(nextRow);

    nextRow.getInnerNodeList().add(node);
    graph.getAllRows().add(nextRow);
    nextRow = new MutableNodeRow(graph, nextRow.getRowIndex() + 1);
    return node;
  }

  private void addParent(MutableNode node, Hash parentHash, Branch branch) {
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

  private MutableNode createNode(Hash hash, Branch branch) {
    return new MutableNode(branch, hash);
  }

  private void append(@NotNull VcsCommit vcsCommit) {
    MutableNode node = addCurrentCommitAndFinishRow(vcsCommit.getHash());

    List<Hash> parents = vcsCommit.getParents();
    Branch branch = node.getBranch();
    if (parents.size() == 1) {
      addParent(node, parents.get(0), branch);
    }
    else {
      for (Hash parentHash : parents) {
        Collection<VcsRef> refs = findRefForHash(node.getCommitHash());
        addParent(node, parentHash, new Branch(node.getCommitHash(), parentHash, refs, branch.getRepositoryRoot()));
      }
    }
  }


  private void lastActions() {
    Set<Hash> notReadiedCommitHashes = underdoneNodes.keySet();
    for (Hash hash : notReadiedCommitHashes) {
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
  MutableGraph runBuild(@NotNull List<? extends VcsCommit> commitParentses) {
    if (commitParentses.size() == 0) {
      throw new IllegalArgumentException("Empty list commitParentses");
    }
    for (VcsCommit vcsCommit : commitParentses) {
      append(vcsCommit);
    }
    lastActions();
    graph.updateVisibleRows();
    return graph;
  }

}
