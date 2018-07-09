// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.code.cfg.ExceptionRangeCFG;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.FastExtendedPostdominanceHelper;
import org.jetbrains.java.decompiler.modules.decompiler.deobfuscator.IrreducibleCFGDeobfuscator;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.util.FastFixedSetFactory;
import org.jetbrains.java.decompiler.util.FastFixedSetFactory.FastFixedSet;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;

public class DomHelper {


  private static RootStatement graphToStatement(ControlFlowGraph graph) {

    VBStyleCollection<Statement, Integer> stats = new VBStyleCollection<>();
    VBStyleCollection<BasicBlock, Integer> blocks = graph.getBlocks();

    for (BasicBlock block : blocks) {
      stats.addWithKey(new BasicBlockStatement(block), block.id);
    }

    BasicBlock firstblock = graph.getFirst();
    // head statement
    Statement firstst = stats.getWithKey(firstblock.id);
    // dummy exit statement
    DummyExitStatement dummyexit = new DummyExitStatement();

    Statement general;
    if (stats.size() > 1 || firstblock.isSuccessor(firstblock)) { // multiple basic blocks or an infinite loop of one block
      general = new GeneralStatement(firstst, stats, null);
    }
    else { // one straightforward basic block
      RootStatement root = new RootStatement(firstst, dummyexit);
      firstst.addSuccessor(new StatEdge(StatEdge.TYPE_BREAK, firstst, dummyexit, root));

      return root;
    }

    for (BasicBlock block : blocks) {
      Statement stat = stats.getWithKey(block.id);

      for (BasicBlock succ : block.getSuccs()) {
        Statement stsucc = stats.getWithKey(succ.id);

        int type;
        if (stsucc == firstst) {
          type = StatEdge.TYPE_CONTINUE;
        }
        else if (graph.getFinallyExits().contains(block)) {
          type = StatEdge.TYPE_FINALLYEXIT;
          stsucc = dummyexit;
        }
        else if (succ.id == graph.getLast().id) {
          type = StatEdge.TYPE_BREAK;
          stsucc = dummyexit;
        }
        else {
          type = StatEdge.TYPE_REGULAR;
        }

        stat.addSuccessor(new StatEdge(type, stat, (type == StatEdge.TYPE_CONTINUE) ? general : stsucc,
                                       (type == StatEdge.TYPE_REGULAR) ? null : general));
      }

      // exceptions edges
      for (BasicBlock succex : block.getSuccExceptions()) {
        Statement stsuccex = stats.getWithKey(succex.id);

        ExceptionRangeCFG range = graph.getExceptionRange(succex, block);
        if (!range.isCircular()) {
          stat.addSuccessor(new StatEdge(stat, stsuccex, range.getExceptionTypes()));
        }
      }
    }

    general.buildContinueSet();
    general.buildMonitorFlags();
    return new RootStatement(general, dummyexit);
  }

  public static VBStyleCollection<List<Integer>, Integer> calcPostDominators(Statement container) {

    HashMap<Statement, FastFixedSet<Statement>> lists = new HashMap<>();

    StrongConnectivityHelper schelper = new StrongConnectivityHelper(container);
    List<List<Statement>> components = schelper.getComponents();

    List<Statement> lstStats = container.getPostReversePostOrderList(StrongConnectivityHelper.getExitReps(components));

    FastFixedSetFactory<Statement> factory = new FastFixedSetFactory<>(lstStats);

    FastFixedSet<Statement> setFlagNodes = factory.spawnEmptySet();
    setFlagNodes.setAllElements();

    FastFixedSet<Statement> initSet = factory.spawnEmptySet();
    initSet.setAllElements();

    for (List<Statement> lst : components) {
      FastFixedSet<Statement> tmpSet;

      if (StrongConnectivityHelper.isExitComponent(lst)) {
        tmpSet = factory.spawnEmptySet();
        tmpSet.addAll(lst);
      }
      else {
        tmpSet = initSet.getCopy();
      }

      for (Statement stat : lst) {
        lists.put(stat, tmpSet);
      }
    }

    do {

      for (Statement stat : lstStats) {

        if (!setFlagNodes.contains(stat)) {
          continue;
        }
        setFlagNodes.remove(stat);

        FastFixedSet<Statement> doms = lists.get(stat);
        FastFixedSet<Statement> domsSuccs = factory.spawnEmptySet();

        List<Statement> lstSuccs = stat.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_FORWARD);
        for (int j = 0; j < lstSuccs.size(); j++) {
          Statement succ = lstSuccs.get(j);
          FastFixedSet<Statement> succlst = lists.get(succ);

          if (j == 0) {
            domsSuccs.union(succlst);
          }
          else {
            domsSuccs.intersection(succlst);
          }
        }

        if (!domsSuccs.contains(stat)) {
          domsSuccs.add(stat);
        }

        if (!InterpreterUtil.equalObjects(domsSuccs, doms)) {

          lists.put(stat, domsSuccs);

          List<Statement> lstPreds = stat.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_BACKWARD);
          for (Statement pred : lstPreds) {
            setFlagNodes.add(pred);
          }
        }
      }
    }
    while (!setFlagNodes.isEmpty());

    VBStyleCollection<List<Integer>, Integer> ret = new VBStyleCollection<>();
    List<Statement> lstRevPost = container.getReversePostOrderList(); // sort order crucial!

    final HashMap<Integer, Integer> mapSortOrder = new HashMap<>();
    for (int i = 0; i < lstRevPost.size(); i++) {
      mapSortOrder.put(lstRevPost.get(i).id, i);
    }

    for (Statement st : lstStats) {

      List<Integer> lstPosts = new ArrayList<>();
      for (Statement stt : lists.get(st)) {
        lstPosts.add(stt.id);
      }

      lstPosts.sort(Comparator.comparing(mapSortOrder::get));

      if (lstPosts.size() > 1 && lstPosts.get(0).intValue() == st.id) {
        lstPosts.add(lstPosts.remove(0));
      }

      ret.addWithKey(lstPosts, st.id);
    }

    return ret;
  }

  public static RootStatement parseGraph(ControlFlowGraph graph) {

    RootStatement root = graphToStatement(graph);

    if (!processStatement(root, new HashMap<>())) {

      //			try {
      //				DotExporter.toDotFile(root.getFirst().getStats().get(13), new File("c:\\Temp\\stat1.dot"));
      //			} catch (Exception ex) {
      //				ex.printStackTrace();
      //			}
      throw new RuntimeException("parsing failure!");
    }

    LabelHelper.lowContinueLabels(root, new HashSet<>());

    SequenceHelper.condenseSequences(root);
    root.buildMonitorFlags();

    // build synchronized statements
    buildSynchronized(root);

    return root;
  }

  public static void removeSynchronizedHandler(Statement stat) {

    for (Statement st : stat.getStats()) {
      removeSynchronizedHandler(st);
    }

    if (stat.type == Statement.TYPE_SYNCRONIZED) {
      ((SynchronizedStatement)stat).removeExc();
    }
  }


  private static void buildSynchronized(Statement stat) {

    for (Statement st : stat.getStats()) {
      buildSynchronized(st);
    }

    if (stat.type == Statement.TYPE_SEQUENCE) {

      while (true) {

        boolean found = false;

        List<Statement> lst = stat.getStats();
        for (int i = 0; i < lst.size() - 1; i++) {
          Statement current = lst.get(i);  // basic block

          if (current.isMonitorEnter()) {

            Statement next = lst.get(i + 1);
            Statement nextDirect = next;

            while (next.type == Statement.TYPE_SEQUENCE) {
              next = next.getFirst();
            }

            if (next.type == Statement.TYPE_CATCHALL) {

              CatchAllStatement ca = (CatchAllStatement)next;

              if (ca.getFirst().isContainsMonitorExit() && ca.getHandler().isContainsMonitorExit()) {

                // remove the head block from sequence
                current.removeSuccessor(current.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL).get(0));

                for (StatEdge edge : current.getPredecessorEdges(Statement.STATEDGE_DIRECT_ALL)) {
                  current.removePredecessor(edge);
                  edge.getSource().changeEdgeNode(Statement.DIRECTION_FORWARD, edge, nextDirect);
                  nextDirect.addPredecessor(edge);
                }

                stat.getStats().removeWithKey(current.id);
                stat.setFirst(stat.getStats().get(0));

                // new statement
                SynchronizedStatement sync = new SynchronizedStatement(current, ca.getFirst(), ca.getHandler());
                sync.setAllParent();

                for (StatEdge edge : new HashSet<>(ca.getLabelEdges())) {
                  sync.addLabeledEdge(edge);
                }

                current.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, current, ca.getFirst()));

                ca.getParent().replaceStatement(ca, sync);
                found = true;
                break;
              }
            }
          }
        }

        if (!found) {
          break;
        }
      }
    }
  }

  private static boolean processStatement(Statement general, HashMap<Integer, Set<Integer>> mapExtPost) {

    if (general.type == Statement.TYPE_ROOT) {
      Statement stat = general.getFirst();
      if (stat.type != Statement.TYPE_GENERAL) {
        return true;
      }
      else {
        boolean complete = processStatement(stat, mapExtPost);
        if (complete) {
          // replace general purpose statement with simple one
          general.replaceStatement(stat, stat.getFirst());
        }
        return complete;
      }
    }

    boolean mapRefreshed = mapExtPost.isEmpty();

    for (int mapstage = 0; mapstage < 2; mapstage++) {

      for (int reducibility = 0;
           reducibility < 5;
           reducibility++) { // FIXME: implement proper node splitting. For now up to 5 nodes in sequence are splitted.

        if (reducibility > 0) {

          //					try {
          //						DotExporter.toDotFile(general, new File("c:\\Temp\\stat1.dot"));
          //					} catch(Exception ex) {ex.printStackTrace();}

          // take care of irreducible control flow graphs
          if (IrreducibleCFGDeobfuscator.isStatementIrreducible(general)) {
            if (!IrreducibleCFGDeobfuscator.splitIrreducibleNode(general)) {
              DecompilerContext.getLogger().writeMessage("Irreducible statement cannot be decomposed!", IFernflowerLogger.Severity.ERROR);
              break;
            }
          }
          else {
            if (mapstage == 2 || mapRefreshed) { // last chance lost
              DecompilerContext.getLogger().writeMessage("Statement cannot be decomposed although reducible!", IFernflowerLogger.Severity.ERROR);
            }
            break;
          }

          //					try {
          //						DotExporter.toDotFile(general, new File("c:\\Temp\\stat1.dot"));
          //					} catch(Exception ex) {ex.printStackTrace();}

          mapExtPost = new HashMap<>();
          mapRefreshed = true;
        }

        for (int i = 0; i < 2; i++) {

          boolean forceall = i != 0;

          while (true) {

            if (findSimpleStatements(general, mapExtPost)) {
              reducibility = 0;
            }

            if (general.type == Statement.TYPE_PLACEHOLDER) {
              return true;
            }

            Statement stat = findGeneralStatement(general, forceall, mapExtPost);

            if (stat != null) {
              boolean complete = processStatement(stat, general.getFirst() == stat ? mapExtPost : new HashMap<>());

              if (complete) {
                // replace general purpose statement with simple one
                general.replaceStatement(stat, stat.getFirst());
              }
              else {
                return false;
              }

              mapExtPost = new HashMap<>();
              mapRefreshed = true;
              reducibility = 0;
            }
            else {
              break;
            }
          }
        }

        //				try {
        //					DotExporter.toDotFile(general, new File("c:\\Temp\\stat1.dot"));
        //				} catch (Exception ex) {
        //					ex.printStackTrace();
        //				}
      }

      if (mapRefreshed) {
        break;
      }
      else {
        mapExtPost = new HashMap<>();
      }
    }

    return false;
  }

  private static Statement findGeneralStatement(Statement stat, boolean forceall, HashMap<Integer, Set<Integer>> mapExtPost) {

    VBStyleCollection<Statement, Integer> stats = stat.getStats();
    VBStyleCollection<List<Integer>, Integer> vbPost;

    if (mapExtPost.isEmpty()) {
      FastExtendedPostdominanceHelper extpost = new FastExtendedPostdominanceHelper();
      mapExtPost.putAll(extpost.getExtendedPostdominators(stat));
    }

    if (forceall) {
      vbPost = new VBStyleCollection<>();
      List<Statement> lstAll = stat.getPostReversePostOrderList();

      for (Statement st : lstAll) {
        Set<Integer> set = mapExtPost.get(st.id);
        if (set != null) {
          vbPost.addWithKey(new ArrayList<>(set), st.id); // FIXME: sort order!!
        }
      }

      // tail statements
      Set<Integer> setFirst = mapExtPost.get(stat.getFirst().id);
      if (setFirst != null) {
        for (Integer id : setFirst) {
          List<Integer> lst = vbPost.getWithKey(id);
          if (lst == null) {
            vbPost.addWithKey(lst = new ArrayList<>(), id);
          }
          lst.add(id);
        }
      }
    }
    else {
      vbPost = calcPostDominators(stat);
    }

    for (int k = 0; k < vbPost.size(); k++) {

      Integer headid = vbPost.getKey(k);
      List<Integer> posts = vbPost.get(k);

      if (!mapExtPost.containsKey(headid) &&
          !(posts.size() == 1 && posts.get(0).equals(headid))) {
        continue;
      }

      Statement head = stats.getWithKey(headid);

      Set<Integer> setExtPosts = mapExtPost.get(headid);

      for (Integer postId : posts) {
        if (!postId.equals(headid) && !setExtPosts.contains(postId)) {
          continue;
        }

        Statement post = stats.getWithKey(postId);

        if (post == null) { // possible in case of an inherited postdominance set
          continue;
        }

        boolean same = (post == head);

        HashSet<Statement> setNodes = new HashSet<>();
        HashSet<Statement> setPreds = new HashSet<>();

        // collect statement nodes
        HashSet<Statement> setHandlers = new HashSet<>();
        setHandlers.add(head);
        while (true) {

          boolean hdfound = false;
          for (Statement handler : setHandlers) {
            if (setNodes.contains(handler)) {
              continue;
            }

            boolean addhd = (setNodes.size() == 0); // first handler == head
            if (!addhd) {
              List<Statement> hdsupp = handler.getNeighbours(StatEdge.TYPE_EXCEPTION, Statement.DIRECTION_BACKWARD);
              addhd = (setNodes.containsAll(hdsupp) && (setNodes.size() > hdsupp.size()
                                                        || setNodes.size() == 1)); // strict subset
            }

            if (addhd) {
              LinkedList<Statement> lstStack = new LinkedList<>();
              lstStack.add(handler);

              while (!lstStack.isEmpty()) {
                Statement st = lstStack.remove(0);

                if (!(setNodes.contains(st) || (!same && st == post))) {
                  setNodes.add(st);
                  if (st != head) {
                    // record predeccessors except for the head
                    setPreds.addAll(st.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_BACKWARD));
                  }

                  // put successors on the stack
                  lstStack.addAll(st.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_FORWARD));

                  // exception edges
                  setHandlers.addAll(st.getNeighbours(StatEdge.TYPE_EXCEPTION, Statement.DIRECTION_FORWARD));
                }
              }

              hdfound = true;
              setHandlers.remove(handler);
              break;
            }
          }

          if (!hdfound) {
            break;
          }
        }

        // check exception handlers
        setHandlers.clear();
        for (Statement st : setNodes) {
          setHandlers.addAll(st.getNeighbours(StatEdge.TYPE_EXCEPTION, Statement.DIRECTION_FORWARD));
        }
        setHandlers.removeAll(setNodes);

        boolean excok = true;
        for (Statement handler : setHandlers) {
          if (!handler.getNeighbours(StatEdge.TYPE_EXCEPTION, Statement.DIRECTION_BACKWARD).containsAll(setNodes)) {
            excok = false;
            break;
          }
        }

        // build statement and return
        if (excok) {
          Statement res;

          setPreds.removeAll(setNodes);
          if (setPreds.size() == 0) {
            if ((setNodes.size() > 1 ||
                 head.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_BACKWARD).contains(head))
                && setNodes.size() < stats.size()) {
              if (checkSynchronizedCompleteness(setNodes)) {
                res = new GeneralStatement(head, setNodes, same ? null : post);
                stat.collapseNodesToStatement(res);

                return res;
              }
            }
          }
        }
      }
    }

    return null;
  }

  private static boolean checkSynchronizedCompleteness(Set<Statement> setNodes) {
    // check exit nodes
    for (Statement stat : setNodes) {
      if (stat.isMonitorEnter()) {
        List<StatEdge> lstSuccs = stat.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL);
        if (lstSuccs.size() != 1 || lstSuccs.get(0).getType() != StatEdge.TYPE_REGULAR) {
          return false;
        }

        if (!setNodes.contains(lstSuccs.get(0).getDestination())) {
          return false;
        }
      }
    }

    return true;
  }

  private static boolean findSimpleStatements(Statement stat, HashMap<Integer, Set<Integer>> mapExtPost) {

    boolean found, success = false;

    do {
      found = false;

      List<Statement> lstStats = stat.getPostReversePostOrderList();
      for (Statement st : lstStats) {

        Statement result = detectStatement(st);

        if (result != null) {

          if (stat.type == Statement.TYPE_GENERAL && result.getFirst() == stat.getFirst() &&
              stat.getStats().size() == result.getStats().size()) {
            // mark general statement
            stat.type = Statement.TYPE_PLACEHOLDER;
          }

          stat.collapseNodesToStatement(result);

          // update the postdominator map
          if (!mapExtPost.isEmpty()) {
            HashSet<Integer> setOldNodes = new HashSet<>();
            for (Statement old : result.getStats()) {
              setOldNodes.add(old.id);
            }

            Integer newid = result.id;

            for (Integer key : new ArrayList<>(mapExtPost.keySet())) {
              Set<Integer> set = mapExtPost.get(key);

              int oldsize = set.size();
              set.removeAll(setOldNodes);

              if (setOldNodes.contains(key)) {
                mapExtPost.computeIfAbsent(newid, k -> new HashSet<>()).addAll(set);
                mapExtPost.remove(key);
              }
              else {
                if (set.size() < oldsize) {
                  set.add(newid);
                }
              }
            }
          }


          found = true;
          break;
        }
      }

      if (found) {
        success = true;
      }
    }
    while (found);

    return success;
  }


  private static Statement detectStatement(Statement head) {

    Statement res;

    if ((res = DoStatement.isHead(head)) != null) {
      return res;
    }

    if ((res = SwitchStatement.isHead(head)) != null) {
      return res;
    }

    if ((res = IfStatement.isHead(head)) != null) {
      return res;
    }

    // synchronized statements will be identified later
    // right now they are recognized as catchall

    if ((res = SequenceStatement.isHead2Block(head)) != null) {
      return res;
    }

    if ((res = CatchStatement.isHead(head)) != null) {
      return res;
    }

    if ((res = CatchAllStatement.isHead(head)) != null) {
      return res;
    }

    return null;
  }
}
