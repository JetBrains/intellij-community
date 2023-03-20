// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.sforms;

import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode.DirectNodeType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.LoopType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;

import java.util.*;
import java.util.Map.Entry;


public class FlattenStatementsHelper {

  // statement.id, node.id(direct), node.id(continue)
  private final Map<Integer, String[]> mapDestinationNodes = new HashMap<>();

  // node.id(source), statement.id(destination), edge type
  private final List<Edge> listEdges = new ArrayList<>();

  // node.id(exit), [node.id(source), statement.id(destination)]
  private final Map<String, List<String[]>> mapShortRangeFinallyPathIds = new HashMap<>();

  // node.id(exit), [node.id(source), statement.id(destination)]
  private final Map<String, List<String[]>> mapLongRangeFinallyPathIds = new HashMap<>();

  // positive if branches
  private final Map<String, Integer> mapPosIfBranch = new HashMap<>();

  private DirectGraph graph;

  private RootStatement root;

  public DirectGraph buildDirectGraph(RootStatement root) {

    this.root = root;

    graph = new DirectGraph();

    flattenStatement();

    // dummy exit node
    Statement dummyexit = root.getDummyExit();
    DirectNode node = new DirectNode(DirectNodeType.DIRECT, dummyexit, Integer.toString(dummyexit.id));
    node.exprents = new ArrayList<>();
    graph.nodes.addWithKey(node, node.id);
    mapDestinationNodes.put(dummyexit.id, new String[]{node.id, null});

    setEdges();

    graph.first = graph.nodes.getWithKey(mapDestinationNodes.get(root.id)[0]);
    graph.sortReversePostOrder();

    return graph;
  }

  private void flattenStatement() {

    class StatementStackEntry {
      public final Statement statement;
      public final LinkedList<StackEntry> stackFinally;
      public final List<Exprent> tailExprents;

      public int statementIndex;
      public int edgeIndex;
      public List<StatEdge> succEdges;

      StatementStackEntry(Statement statement, LinkedList<StackEntry> stackFinally, List<Exprent> tailExprents) {
        this.statement = statement;
        this.stackFinally = stackFinally;
        this.tailExprents = tailExprents;
      }
    }

    LinkedList<StatementStackEntry> lstStackStatements = new LinkedList<>();

    lstStackStatements.add(new StatementStackEntry(root, new LinkedList<>(), null));

    mainloop:
    while (!lstStackStatements.isEmpty()) {

      StatementStackEntry statEntry = lstStackStatements.removeFirst();

      Statement stat = statEntry.statement;
      LinkedList<StackEntry> stackFinally = statEntry.stackFinally;
      int statementBreakIndex = statEntry.statementIndex;

      DirectNode node, nd;

      List<StatEdge> lstSuccEdges = new ArrayList<>();
      DirectNode sourcenode = null;

      if (statEntry.succEdges == null) {

        switch (stat.type) {
          case BASIC_BLOCK -> {
            node = new DirectNode(DirectNodeType.DIRECT, stat, (BasicBlockStatement)stat);
            if (stat.getExprents() != null) {
              node.exprents = stat.getExprents();
            }
            graph.nodes.putWithKey(node, node.id);
            mapDestinationNodes.put(stat.id, new String[]{node.id, null});

            lstSuccEdges.addAll(stat.getSuccessorEdges(EdgeType.DIRECT_ALL));
            sourcenode = node;

            List<Exprent> tailExprentList = statEntry.tailExprents;

            if (tailExprentList != null) {
              DirectNode tail = new DirectNode(DirectNodeType.TAIL, stat, stat.id + "_tail");
              tail.exprents = tailExprentList;
              graph.nodes.putWithKey(tail, tail.id);

              mapDestinationNodes.put(-stat.id, new String[]{tail.id, null});
              listEdges.add(new Edge(node.id, -stat.id, EdgeType.REGULAR));

              sourcenode = tail;
            }

            // 'if' statement: record positive branch
            if (stat.getLastBasicType() == StatementType.IF) {
              mapPosIfBranch.put(sourcenode.id, lstSuccEdges.get(0).getDestination().id);
            }
          }
          case CATCH_ALL, TRY_CATCH -> {
            DirectNode firstnd = new DirectNode(DirectNodeType.TRY, stat, stat.id + "_try");

            mapDestinationNodes.put(stat.id, new String[]{firstnd.id, null});
            graph.nodes.putWithKey(firstnd, firstnd.id);

            LinkedList<StatementStackEntry> lst = new LinkedList<>();

            for (Statement st : stat.getStats()) {
              listEdges.add(new Edge(firstnd.id, st.id, EdgeType.REGULAR));

              LinkedList<StackEntry> stack = stackFinally;
              if (stat.type == StatementType.CATCH_ALL && ((CatchAllStatement)stat).isFinally()) {
                stack = new LinkedList<>(stackFinally);

                if (st == stat.getFirst()) { // catch head
                  stack.add(new StackEntry((CatchAllStatement)stat, Boolean.FALSE));
                }
                else { // handler
                  stack.add(new StackEntry((CatchAllStatement)stat, Boolean.TRUE, EdgeType.BREAK,
                                           root.getDummyExit(), st, st, firstnd, firstnd, true));
                }
              }
              lst.add(new StatementStackEntry(st, stack, null));
            }

            lstStackStatements.addAll(0, lst);
          }
          case DO -> {
            if (statementBreakIndex == 0) {
              statEntry.statementIndex = 1;
              lstStackStatements.addFirst(statEntry);
              lstStackStatements.addFirst(new StatementStackEntry(stat.getFirst(), stackFinally, null));

              continue mainloop;
            }

            nd = graph.nodes.getWithKey(mapDestinationNodes.get(stat.getFirst().id)[0]);

            DoStatement dostat = (DoStatement)stat;
            LoopType loopType = dostat.getLoopType();

            if (loopType == LoopType.DO) {
              mapDestinationNodes.put(stat.id, new String[]{nd.id, nd.id});
              break;
            }

            lstSuccEdges.add(stat.getSuccessorEdges(EdgeType.DIRECT_ALL).get(0));  // exactly one edge

            switch (loopType) {
              case WHILE, DO_WHILE -> {
                node = new DirectNode(DirectNodeType.CONDITION, stat, stat.id + "_cond");
                node.exprents = dostat.getConditionExprentList();
                graph.nodes.putWithKey(node, node.id);

                listEdges.add(new Edge(node.id, stat.getFirst().id, EdgeType.REGULAR));

                if (loopType == LoopType.WHILE) {
                  mapDestinationNodes.put(stat.id, new String[]{node.id, node.id});
                }
                else {
                  mapDestinationNodes.put(stat.id, new String[]{nd.id, node.id});

                  boolean found = false;
                  for (Edge edge : listEdges) {
                    if (edge.statid.equals(stat.id) && edge.edgetype == EdgeType.CONTINUE) {
                      found = true;
                      break;
                    }
                  }
                  if (!found) {
                    listEdges.add(new Edge(nd.id, stat.id, EdgeType.CONTINUE));
                  }
                }
                sourcenode = node;
              }
              case FOR -> {
                DirectNode nodeinit = new DirectNode(DirectNodeType.INIT, stat, stat.id + "_init");
                if (dostat.getInitExprent() != null) {
                  nodeinit.exprents = dostat.getInitExprentList();
                }
                graph.nodes.putWithKey(nodeinit, nodeinit.id);

                DirectNode nodecond = new DirectNode(DirectNodeType.CONDITION, stat, stat.id + "_cond");
                nodecond.exprents = dostat.getConditionExprentList();
                graph.nodes.putWithKey(nodecond, nodecond.id);

                DirectNode nodeinc = new DirectNode(DirectNodeType.INCREMENT, stat, stat.id + "_inc");
                nodeinc.exprents = dostat.getIncExprentList();
                graph.nodes.putWithKey(nodeinc, nodeinc.id);

                mapDestinationNodes.put(stat.id, new String[]{nodeinit.id, nodeinc.id});
                mapDestinationNodes.put(-stat.id, new String[]{nodecond.id, null});

                listEdges.add(new Edge(nodecond.id, stat.getFirst().id, EdgeType.REGULAR));
                listEdges.add(new Edge(nodeinit.id, -stat.id, EdgeType.REGULAR));
                listEdges.add(new Edge(nodeinc.id, -stat.id, EdgeType.REGULAR));

                boolean found = false;
                for (Edge edge : listEdges) {
                  if (edge.statid.equals(stat.id) && edge.edgetype == EdgeType.CONTINUE) {
                    found = true;
                    break;
                  }
                }
                if (!found) {
                  listEdges.add(new Edge(nd.id, stat.id, EdgeType.CONTINUE));
                }

                sourcenode = nodecond;
              }
            }
          }
          case SYNCHRONIZED, SWITCH, IF, SEQUENCE, ROOT -> {
            int statsize = stat.getStats().size();
            if (stat.type == StatementType.SYNCHRONIZED) {
              statsize = 2;  // exclude the handler if synchronized
            }

            if (statementBreakIndex <= statsize) {
              List<Exprent> tailexprlst = switch (stat.type) {
                case SYNCHRONIZED -> ((SynchronizedStatement)stat).getHeadexprentList();
                case SWITCH -> ((SwitchStatement)stat).getHeadExprentList();
                case IF -> ((IfStatement)stat).getHeadexprentList();
                default -> null;
              };

              for (int i = statementBreakIndex; i < statsize; i++) {
                statEntry.statementIndex = i + 1;
                lstStackStatements.addFirst(statEntry);
                lstStackStatements.addFirst(
                  new StatementStackEntry(stat.getStats().get(i), stackFinally,
                                          (i == 0 && tailexprlst != null && tailexprlst.get(0) != null) ? tailexprlst : null));

                continue mainloop;
              }

              node = graph.nodes.getWithKey(mapDestinationNodes.get(stat.getFirst().id)[0]);
              mapDestinationNodes.put(stat.id, new String[]{node.id, null});

              if (stat.type == StatementType.IF && ((IfStatement)stat).iftype == IfStatement.IFTYPE_IF) {
                lstSuccEdges.add(stat.getSuccessorEdges(EdgeType.DIRECT_ALL).get(0));  // exactly one edge
                sourcenode = tailexprlst.get(0) == null ? node : graph.nodes.getWithKey(node.id + "_tail");
              }
            }
          }
        }
      }

      // no successor edges
      if (sourcenode != null) {

        if (statEntry.succEdges != null) {
          lstSuccEdges = statEntry.succEdges;
        }

        for (int edgeindex = statEntry.edgeIndex; edgeindex < lstSuccEdges.size(); edgeindex++) {

          StatEdge edge = lstSuccEdges.get(edgeindex);

          LinkedList<StackEntry> stack = new LinkedList<>(stackFinally);

          EdgeType edgetype = edge.getType();
          Statement destination = edge.getDestination();

          DirectNode finallyShortRangeSource = sourcenode;
          DirectNode finallyLongRangeSource = sourcenode;
          Statement finallyShortRangeEntry = null;
          Statement finallyLongRangeEntry = null;

          boolean isFinallyMonitorExceptionPath = false;

          boolean isFinallyExit = false;

          while (true) {

            StackEntry entry = null;
            if (!stack.isEmpty()) {
              entry = stack.getLast();
            }

            boolean created = true;

            if (entry == null) {
              saveEdge(sourcenode, destination, edgetype, isFinallyExit ? finallyShortRangeSource : null, finallyLongRangeSource,
                       finallyShortRangeEntry, finallyLongRangeEntry, isFinallyMonitorExceptionPath);
            }
            else {

              CatchAllStatement catchall = entry.catchstatement;

              if (entry.state) { // finally handler statement
                if (edgetype == EdgeType.FINALLY_EXIT) {

                  stack.removeLast();
                  destination = entry.destination;
                  edgetype = entry.edgetype;

                  finallyShortRangeSource = entry.finallyShortRangeSource;
                  finallyLongRangeSource = entry.finallyLongRangeSource;
                  finallyShortRangeEntry = entry.finallyShortRangeEntry;
                  finallyLongRangeEntry = entry.finallyLongRangeEntry;

                  isFinallyExit = true;
                  isFinallyMonitorExceptionPath = (catchall.getMonitor() != null) & entry.isFinallyExceptionPath;

                  created = false;
                }
                else {
                  if (!catchall.containsStatementStrict(destination)) {
                    stack.removeLast();
                    created = false;
                  }
                  else {
                    saveEdge(sourcenode, destination, edgetype, isFinallyExit ? finallyShortRangeSource : null, finallyLongRangeSource,
                             finallyShortRangeEntry, finallyLongRangeEntry, isFinallyMonitorExceptionPath);
                  }
                }
              }
              else { // finally protected try statement
                if (!catchall.containsStatementStrict(destination)) {
                  saveEdge(sourcenode, catchall.getHandler(), EdgeType.REGULAR, isFinallyExit ? finallyShortRangeSource : null,
                           finallyLongRangeSource, finallyShortRangeEntry, finallyLongRangeEntry, isFinallyMonitorExceptionPath);

                  stack.removeLast();
                  stack.add(new StackEntry(catchall, Boolean.TRUE, edgetype, destination, catchall.getHandler(),
                                           finallyLongRangeEntry == null ? catchall.getHandler() : finallyLongRangeEntry,
                                           sourcenode, finallyLongRangeSource, false));

                  statEntry.edgeIndex = edgeindex + 1;
                  statEntry.succEdges = lstSuccEdges;
                  lstStackStatements.addFirst(statEntry);
                  lstStackStatements.addFirst(new StatementStackEntry(catchall.getHandler(), stack, null));

                  continue mainloop;
                }
                else {
                  saveEdge(sourcenode, destination, edgetype, isFinallyExit ? finallyShortRangeSource : null, finallyLongRangeSource,
                           finallyShortRangeEntry, finallyLongRangeEntry, isFinallyMonitorExceptionPath);
                }
              }
            }

            if (created) {
              break;
            }
          }
        }
      }
    }
  }

  private void saveEdge(DirectNode sourcenode,
                        Statement destination,
                        EdgeType edgetype,
                        DirectNode finallyShortRangeSource,
                        DirectNode finallyLongRangeSource,
                        Statement finallyShortRangeEntry,
                        Statement finallyLongRangeEntry,
                        boolean isFinallyMonitorExceptionPath) {

    if (edgetype != EdgeType.FINALLY_EXIT) {
      listEdges.add(new Edge(sourcenode.id, destination.id, edgetype));
    }

    if (finallyShortRangeSource != null) {
      boolean isContinueEdge = (edgetype == EdgeType.CONTINUE);

      mapShortRangeFinallyPathIds.computeIfAbsent(sourcenode.id, k -> new ArrayList<>()).add(new String[]{
        finallyShortRangeSource.id,
        Integer.toString(destination.id),
        Integer.toString(finallyShortRangeEntry.id),
        isFinallyMonitorExceptionPath ? "1" : null,
        isContinueEdge ? "1" : null});

      mapLongRangeFinallyPathIds.computeIfAbsent(sourcenode.id, k -> new ArrayList<>()).add(new String[]{
        finallyLongRangeSource.id,
        Integer.toString(destination.id),
        Integer.toString(finallyLongRangeEntry.id),
        isContinueEdge ? "1" : null});
    }
  }

  private void setEdges() {

    for (Edge edge : listEdges) {

      String sourceid = edge.sourceid;
      Integer statid = edge.statid;

      DirectNode source = graph.nodes.getWithKey(sourceid);

      DirectNode dest = graph.nodes.getWithKey(mapDestinationNodes.get(statid)[edge.edgetype == EdgeType.CONTINUE ? 1 : 0]);

      if (!source.successors.contains(dest)) {
        source.successors.add(dest);
      }

      if (!dest.predecessors.contains(source)) {
        dest.predecessors.add(source);
      }

      if (mapPosIfBranch.containsKey(sourceid) && !statid.equals(mapPosIfBranch.get(sourceid))) {
        graph.mapNegIfBranch.put(sourceid, dest.id);
      }
    }

    for (int i = 0; i < 2; i++) {
      for (Entry<String, List<String[]>> ent : (i == 0 ? mapShortRangeFinallyPathIds : mapLongRangeFinallyPathIds).entrySet()) {

        List<FinallyPathWrapper> newLst = new ArrayList<>();

        List<String[]> lst = ent.getValue();
        for (String[] arr : lst) {

          boolean isContinueEdge = arr[i == 0 ? 4 : 3] != null;

          DirectNode dest = graph.nodes.getWithKey(mapDestinationNodes.get(Integer.parseInt(arr[1]))[isContinueEdge ? 1 : 0]);
          DirectNode enter = graph.nodes.getWithKey(mapDestinationNodes.get(Integer.parseInt(arr[2]))[0]);

          newLst.add(new FinallyPathWrapper(arr[0], dest.id, enter.id));

          if (i == 0 && arr[3] != null) {
            graph.mapFinallyMonitorExceptionPathExits.put(ent.getKey(), dest.id);
          }
        }

        if (!newLst.isEmpty()) {
          (i == 0 ? graph.mapShortRangeFinallyPaths : graph.mapLongRangeFinallyPaths).put(ent.getKey(),
                                                                                          new ArrayList<>(
                                                                                            new HashSet<>(newLst)));
        }
      }
    }
  }

  public Map<Integer, String[]> getMapDestinationNodes() {
    return mapDestinationNodes;
  }

  public static final class FinallyPathWrapper {
    public final String source;
    public final String destination;
    public final String entry;

    private FinallyPathWrapper(String source, String destination, String entry) {
      this.source = source;
      this.destination = destination;
      this.entry = entry;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof FinallyPathWrapper fpw)) return false;

      return (source + ":" + destination + ":" + entry).equals(fpw.source + ":" + fpw.destination + ":" + fpw.entry);
    }

    @Override
    public int hashCode() {
      return (source + ":" + destination + ":" + entry).hashCode();
    }

    @Override
    public String toString() {
      return source + "->(" + entry + ")->" + destination;
    }
  }


  private static class StackEntry {

    public final CatchAllStatement catchstatement;
    public final boolean state;
    public final EdgeType edgetype;
    public final boolean isFinallyExceptionPath;

    public final Statement destination;
    public final Statement finallyShortRangeEntry;
    public final Statement finallyLongRangeEntry;
    public final DirectNode finallyShortRangeSource;
    public final DirectNode finallyLongRangeSource;

    StackEntry(CatchAllStatement catchstatement,
               boolean state,
               EdgeType edgetype,
               Statement destination,
               Statement finallyShortRangeEntry,
               Statement finallyLongRangeEntry,
               DirectNode finallyShortRangeSource,
               DirectNode finallyLongRangeSource,
               boolean isFinallyExceptionPath) {

      this.catchstatement = catchstatement;
      this.state = state;
      this.edgetype = edgetype;
      this.isFinallyExceptionPath = isFinallyExceptionPath;

      this.destination = destination;
      this.finallyShortRangeEntry = finallyShortRangeEntry;
      this.finallyLongRangeEntry = finallyLongRangeEntry;
      this.finallyShortRangeSource = finallyShortRangeSource;
      this.finallyLongRangeSource = finallyLongRangeSource;
    }

    StackEntry(CatchAllStatement catchstatement, boolean state) {
      this(catchstatement, state, EdgeType.NULL, null, null, null, null, null, false);
    }
  }

  private static class Edge {
    public final String sourceid;
    public final Integer statid;
    public final EdgeType edgetype;

    Edge(String sourceid, Integer statid, EdgeType edgetype) {
      this.sourceid = sourceid;
      this.statid = statid;
      this.edgetype = edgetype;
    }
  }
}
