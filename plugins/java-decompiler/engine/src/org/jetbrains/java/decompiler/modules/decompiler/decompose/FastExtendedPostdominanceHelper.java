// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.decompose;

import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.util.FastFixedSetFactory;
import org.jetbrains.java.decompiler.util.FastFixedSetFactory.FastFixedSet;

import java.util.*;
import java.util.Map.Entry;

public class FastExtendedPostdominanceHelper {

  private List<Statement> lstReversePostOrderList;

  private HashMap<Integer, FastFixedSet<Integer>> mapSupportPoints = new HashMap<>();

  private final HashMap<Integer, FastFixedSet<Integer>> mapExtPostdominators = new HashMap<>();

  private Statement statement;

  private FastFixedSetFactory<Integer> factory;

  public HashMap<Integer, Set<Integer>> getExtendedPostdominators(Statement statement) {

    this.statement = statement;

    HashSet<Integer> set = new HashSet<>();
    for (Statement st : statement.getStats()) {
      set.add(st.id);
    }
    this.factory = new FastFixedSetFactory<>(set);

    lstReversePostOrderList = statement.getReversePostOrderList();

    //		try {
    //			DotExporter.toDotFile(statement, new File("c:\\Temp\\stat1.dot"));
    //		} catch (Exception ex) {
    //			ex.printStackTrace();
    //		}

    calcDefaultReachableSets();

    removeErroneousNodes();

    DominatorTreeExceptionFilter filter = new DominatorTreeExceptionFilter(statement);
    filter.initialize();

    filterOnExceptionRanges(filter);

    filterOnDominance(filter);

    Set<Entry<Integer, FastFixedSet<Integer>>> entries = mapExtPostdominators.entrySet();
    HashMap<Integer, Set<Integer>> res = new HashMap<>(entries.size());
    for (Entry<Integer, FastFixedSet<Integer>> entry : entries) {
      res.put(entry.getKey(), entry.getValue().toPlainSet());
    }

    return res;
  }


  private void filterOnDominance(DominatorTreeExceptionFilter filter) {

    DominatorEngine engine = filter.getDomEngine();

    for (Integer head : new HashSet<>(mapExtPostdominators.keySet())) {

      FastFixedSet<Integer> setPostdoms = mapExtPostdominators.get(head);

      LinkedList<Statement> stack = new LinkedList<>();
      LinkedList<FastFixedSet<Integer>> stackPath = new LinkedList<>();

      stack.add(statement.getStats().getWithKey(head));
      stackPath.add(factory.spawnEmptySet());

      Set<Statement> setVisited = new HashSet<>();

      setVisited.add(stack.getFirst());

      while (!stack.isEmpty()) {

        Statement stat = stack.removeFirst();
        FastFixedSet<Integer> path = stackPath.removeFirst();

        if (setPostdoms.contains(stat.id)) {
          path.add(stat.id);
        }

        if (path.contains(setPostdoms)) {
          continue;
        }

        if(!engine.isDominator(stat.id, head)) {
          setPostdoms.complement(path);
          continue;
        }

        for (StatEdge edge : stat.getSuccessorEdges(EdgeType.REGULAR)) {

          Statement edge_destination = edge.getDestination();

          if(!setVisited.contains(edge_destination)) {

            stack.add(edge_destination);
            stackPath.add(path.getCopy());

            setVisited.add(edge_destination);
          }
        }
      }

      if (setPostdoms.isEmpty()) {
        mapExtPostdominators.remove(head);
      }
    }
  }

  private void filterOnExceptionRanges(DominatorTreeExceptionFilter filter) {
    for (Integer head : new HashSet<>(mapExtPostdominators.keySet())) {
      FastFixedSet<Integer> set = mapExtPostdominators.get(head);
      for (Iterator<Integer> it = set.iterator(); it.hasNext(); ) {
        if (!filter.acceptStatementPair(head, it.next())) {
          it.remove();
        }
      }
      if (set.isEmpty()) {
        mapExtPostdominators.remove(head);
      }
    }
  }

  private void removeErroneousNodes() {
    mapSupportPoints = new HashMap<>();

    calcReachabilitySuppPoints(EdgeType.REGULAR);

    iterateReachability((node, mapSets) -> {
      Integer nodeid = node.id;

      FastFixedSet<Integer> setReachability = mapSets.get(nodeid);
      List<FastFixedSet<Integer>> lstPredSets = new ArrayList<>();

      for (StatEdge prededge : node.getPredecessorEdges(EdgeType.REGULAR)) {
        FastFixedSet<Integer> setPred = mapSets.get(prededge.getSource().id);
        if (setPred == null) {
          setPred = mapSupportPoints.get(prededge.getSource().id);
        }

        // setPred cannot be empty as it is a reachability set
        lstPredSets.add(setPred);
      }

      for (Integer id : setReachability) {

        FastFixedSet<Integer> setReachabilityCopy = setReachability.getCopy();

        FastFixedSet<Integer> setIntersection = factory.spawnEmptySet();
        boolean isIntersectionInitialized = false;

        for (FastFixedSet<Integer> predset : lstPredSets) {
          if (predset.contains(id)) {
            if (!isIntersectionInitialized) {
              setIntersection.union(predset);
              isIntersectionInitialized = true;
            }
            else {
              setIntersection.intersection(predset);
            }
          }
        }

        if (nodeid != id.intValue()) {
          setIntersection.add(nodeid);
        }
        else {
          setIntersection.remove(nodeid);
        }

        setReachabilityCopy.complement(setIntersection);

        mapExtPostdominators.get(id).complement(setReachabilityCopy);
      }

      return false;
    }, EdgeType.REGULAR);

    // exception handlers cannot be postdominator nodes
    // TODO: replace with a standard set?
    FastFixedSet<Integer> setHandlers = factory.spawnEmptySet();
    boolean handlerfound = false;

    for (Statement stat : statement.getStats()) {
      if (stat.getPredecessorEdges(EdgeType.DIRECT_ALL).isEmpty() &&
          !stat.getPredecessorEdges(EdgeType.EXCEPTION).isEmpty()) { // exception handler
        setHandlers.add(stat.id);
        handlerfound = true;
      }
    }

    if (handlerfound) {
      for (FastFixedSet<Integer> set : mapExtPostdominators.values()) {
        set.complement(setHandlers);
      }
    }
  }

  private void calcDefaultReachableSets() {
    EdgeType edgetype = EdgeType.REGULAR.unite(EdgeType.EXCEPTION);

    calcReachabilitySuppPoints(edgetype);

    for (Statement stat : statement.getStats()) {
      mapExtPostdominators.put(stat.id, factory.spawnEmptySet());
    }

    iterateReachability((node, mapSets) -> {
      Integer nodeid = node.id;
      FastFixedSet<Integer> setReachability = mapSets.get(nodeid);

      for (Integer id : setReachability) {
        mapExtPostdominators.get(id).add(nodeid);
      }

      return false;
    }, edgetype);
  }

  private void calcReachabilitySuppPoints(final EdgeType edgetype) {
    iterateReachability((node, mapSets) -> {
      // consider to be a support point
      for (StatEdge sucedge : node.getAllSuccessorEdges()) {
        if ((sucedge.getType().mask() & edgetype.mask()) != 0) {
          if (mapSets.containsKey(sucedge.getDestination().id)) {
            FastFixedSet<Integer> setReachability = mapSets.get(node.id);

            if (!Objects.equals(setReachability, mapSupportPoints.get(node.id))) {
              mapSupportPoints.put(node.id, setReachability);
              return true;
            }
          }
        }
      }

      return false;
    }, edgetype);
  }

  private void iterateReachability(IReachabilityAction action, EdgeType edgetype) {
    while (true) {
      boolean iterate = false;

      HashMap<Integer, FastFixedSet<Integer>> mapSets = new HashMap<>();

      for (Statement stat : lstReversePostOrderList) {

        FastFixedSet<Integer> set = factory.spawnEmptySet();
        set.add(stat.id);

        for (StatEdge prededge : stat.getAllPredecessorEdges()) {
          if ((prededge.getType().mask() & edgetype.mask()) != 0) {
            Statement pred = prededge.getSource();

            FastFixedSet<Integer> setPred = mapSets.get(pred.id);
            if (setPred == null) {
              setPred = mapSupportPoints.get(pred.id);
            }

            if (setPred != null) {
              set.union(setPred);
            }
          }
        }

        mapSets.put(stat.id, set);

        if (action != null) {
          iterate |= action.action(stat, mapSets);
        }

        // remove reachability information of fully processed nodes (saves memory)
        for (StatEdge prededge : stat.getAllPredecessorEdges()) {
          if ((prededge.getType().mask() & edgetype.mask()) != 0) {
            Statement pred = prededge.getSource();

            if (mapSets.containsKey(pred.id)) {
              boolean remstat = true;
              for (StatEdge sucedge : pred.getAllSuccessorEdges()) {
                if ((sucedge.getType().mask() & edgetype.mask()) != 0) {
                  if (!mapSets.containsKey(sucedge.getDestination().id)) {
                    remstat = false;
                    break;
                  }
                }
              }

              if (remstat) {
                mapSets.put(pred.id, null);
              }
            }
          }
        }
      }

      if (!iterate) {
        break;
      }
    }
  }


  private interface IReachabilityAction {
    boolean action(Statement node, HashMap<Integer, FastFixedSet<Integer>> mapSets);
  }
}
