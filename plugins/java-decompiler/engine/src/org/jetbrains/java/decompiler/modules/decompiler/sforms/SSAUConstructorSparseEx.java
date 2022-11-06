// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.sforms;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper.FinallyPathWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionEdge;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionNode;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionsGraph;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;
import org.jetbrains.java.decompiler.util.SFormsFastMapDirect;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;
import java.util.Map.Entry;

public class SSAUConstructorSparseEx {

  // node id, var, version
  private final HashMap<String, SFormsFastMapDirect> inVarVersions = new HashMap<>();
  //private HashMap<String, HashMap<Integer, FastSet<Integer>>> inVarVersions = new HashMap<String, HashMap<Integer, FastSet<Integer>>>();

  // node id, var, version (direct branch)
  private final HashMap<String, SFormsFastMapDirect> outVarVersions = new HashMap<>();
  //private HashMap<String, HashMap<Integer, FastSet<Integer>>> outVarVersions = new HashMap<String, HashMap<Integer, FastSet<Integer>>>();

  // node id, var, version (negative branch)
  private final HashMap<String, SFormsFastMapDirect> outNegVarVersions = new HashMap<>();
  //private HashMap<String, HashMap<Integer, FastSet<Integer>>> outNegVarVersions = new HashMap<String, HashMap<Integer, FastSet<Integer>>>();

  // node id, var, version
  private final HashMap<String, SFormsFastMapDirect> extraVarVersions = new HashMap<>();
  //private HashMap<String, HashMap<Integer, FastSet<Integer>>> extraVarVersions = new HashMap<String, HashMap<Integer, FastSet<Integer>>>();

  // var, version
  private final HashMap<Integer, Integer> lastversion = new HashMap<>();

  // version, protected ranges (catch, finally)
  private final HashMap<VarVersionPair, Integer> mapVersionFirstRange = new HashMap<>();

  // version, version
  private final HashMap<VarVersionPair, VarVersionPair> phantomppnodes = new HashMap<>(); // ++ and --

  // node.id, version, version
  private final HashMap<String, HashMap<VarVersionPair, VarVersionPair>> phantomexitnodes =
    new HashMap<>(); // finally exits

  // versions memory dependencies
  private final VarVersionsGraph ssuversions = new VarVersionsGraph();

  // field access vars (exprent id, var id)
  private final HashMap<Integer, Integer> mapFieldVars = new HashMap<>();

  // field access counter
  private int fieldvarcounter = -1;

  // set factory
  private FastSparseSetFactory<Integer> factory;

  public void splitVariables(RootStatement root, StructMethod mt) {

    FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
    DirectGraph dgraph = flatthelper.buildDirectGraph(root);

    HashSet<Integer> setInit = new HashSet<>();
    for (int i = 0; i < 64; i++) {
      setInit.add(i);
    }
    factory = new FastSparseSetFactory<>(setInit);

    extraVarVersions.put(dgraph.first.id, createFirstMap(mt, root));

    setCatchMaps(root, dgraph, flatthelper);

    //		try {
    //			DotExporter.toDotFile(dgraph, new File("c:\\Temp\\gr12_my.dot"));
    //		} catch(Exception ex) {ex.printStackTrace();}

    HashSet<String> updated = new HashSet<>();
    do {
      //			System.out.println("~~~~~~~~~~~~~ \r\n"+root.toJava());
      ssaStatements(dgraph, updated, false);
      //			System.out.println("~~~~~~~~~~~~~ \r\n"+root.toJava());
    }
    while (!updated.isEmpty());


    ssaStatements(dgraph, updated, true);

    ssuversions.initDominators();
  }

  private void ssaStatements(DirectGraph dgraph, HashSet<String> updated, boolean calcLiveVars) {

    for (DirectNode node : dgraph.nodes) {

      updated.remove(node.id);
      mergeInVarMaps(node, dgraph);

      SFormsFastMapDirect varmap = new SFormsFastMapDirect(inVarVersions.get(node.id));

      SFormsFastMapDirect[] varmaparr = new SFormsFastMapDirect[]{varmap, null};

      if (node.exprents != null) {
        for (Exprent expr : node.exprents) {
          processExprent(expr, varmaparr, node.statement, calcLiveVars);
        }
      }

      if (varmaparr[1] == null) {
        varmaparr[1] = varmaparr[0];
      }

      // quick solution: 'dummy' field variables should not cross basic block borders (otherwise problems e.g. with finally loops - usage without assignment in a loop)
      // For the full solution consider adding a dummy assignment at the entry point of the method
      boolean allow_field_propagation = node.successors.isEmpty() || (node.successors.size() == 1 && node.successors.get(0).predecessors.size() == 1);

      if (!allow_field_propagation && varmaparr[0] != null) {
        varmaparr[0].removeAllFields();
        varmaparr[1].removeAllFields();
      }

      boolean this_updated = !mapsEqual(varmaparr[0], outVarVersions.get(node.id))
                             || (outNegVarVersions.containsKey(node.id) && !mapsEqual(varmaparr[1], outNegVarVersions.get(node.id)));

      if (this_updated) {

        outVarVersions.put(node.id, varmaparr[0]);
        if (dgraph.mapNegIfBranch.containsKey(node.id)) {
          outNegVarVersions.put(node.id, varmaparr[1]);
        }

        for (DirectNode nd : node.successors) {
          updated.add(nd.id);
        }
      }
    }
  }


  private void processExprent(Exprent expr, SFormsFastMapDirect[] varmaparr, Statement stat, boolean calcLiveVars) {

    if (expr == null) {
      return;
    }


    VarExprent varassign = null;
    boolean finished = false;

    switch (expr.type) {
      case Exprent.EXPRENT_ASSIGNMENT -> {
        AssignmentExprent assexpr = (AssignmentExprent)expr;
        if (assexpr.getCondType() == AssignmentExprent.CONDITION_NONE) {
          Exprent dest = assexpr.getLeft();
          if (dest.type == Exprent.EXPRENT_VAR) {
            varassign = (VarExprent)dest;
          }
        }
      }
      case Exprent.EXPRENT_FUNCTION -> {
        FunctionExprent func = (FunctionExprent)expr;
        switch (func.getFuncType()) {
          case FunctionExprent.FUNCTION_IIF -> {
            processExprent(func.getLstOperands().get(0), varmaparr, stat, calcLiveVars);

            SFormsFastMapDirect varmapFalse;
            if (varmaparr[1] == null) {
              varmapFalse = new SFormsFastMapDirect(varmaparr[0]);
            }
            else {
              varmapFalse = varmaparr[1];
              varmaparr[1] = null;
            }

            processExprent(func.getLstOperands().get(1), varmaparr, stat, calcLiveVars);

            SFormsFastMapDirect[] varmaparrNeg = new SFormsFastMapDirect[]{varmapFalse, null};
            processExprent(func.getLstOperands().get(2), varmaparrNeg, stat, calcLiveVars);

            mergeMaps(varmaparr[0], varmaparrNeg[0]);
            varmaparr[1] = null;

            finished = true;
          }
          case FunctionExprent.FUNCTION_CADD -> {
            processExprent(func.getLstOperands().get(0), varmaparr, stat, calcLiveVars);

            SFormsFastMapDirect[] varmaparrAnd = new SFormsFastMapDirect[]{new SFormsFastMapDirect(varmaparr[0]), null};

            processExprent(func.getLstOperands().get(1), varmaparrAnd, stat, calcLiveVars);

            // false map
            varmaparr[1] = mergeMaps(varmaparr[varmaparr[1] == null ? 0 : 1], varmaparrAnd[varmaparrAnd[1] == null ? 0 : 1]);
            // true map
            varmaparr[0] = varmaparrAnd[0];

            finished = true;
          }
          case FunctionExprent.FUNCTION_COR -> {
            processExprent(func.getLstOperands().get(0), varmaparr, stat, calcLiveVars);

            SFormsFastMapDirect[] varmaparrOr =
              new SFormsFastMapDirect[]{new SFormsFastMapDirect(varmaparr[varmaparr[1] == null ? 0 : 1]), null};

            processExprent(func.getLstOperands().get(1), varmaparrOr, stat, calcLiveVars);

            // false map
            varmaparr[1] = varmaparrOr[varmaparrOr[1] == null ? 0 : 1];
            // true map
            varmaparr[0] = mergeMaps(varmaparr[0], varmaparrOr[0]);

            finished = true;
          }
        }
      }
    }

    if (!finished) {
      List<Exprent> lst = expr.getAllExprents();
      lst.remove(varassign);

      for (Exprent ex : lst) {
        processExprent(ex, varmaparr, stat, calcLiveVars);
      }
    }


    SFormsFastMapDirect varmap = varmaparr[0];

    // field access
    if (expr.type == Exprent.EXPRENT_FIELD) {

      int index;
      if (mapFieldVars.containsKey(expr.id)) {
        index = mapFieldVars.get(expr.id);
      }
      else {
        index = fieldvarcounter--;
        mapFieldVars.put(expr.id, index);

        // ssu graph
        ssuversions.createNode(new VarVersionPair(index, 1));
      }

      setCurrentVar(varmap, index, 1);
    }
    else if (expr.type == Exprent.EXPRENT_INVOCATION ||
             (expr.type == Exprent.EXPRENT_ASSIGNMENT && ((AssignmentExprent)expr).getLeft().type == Exprent.EXPRENT_FIELD) ||
             (expr.type == Exprent.EXPRENT_NEW && ((NewExprent)expr).getNewType().getType() == CodeConstants.TYPE_OBJECT) ||
             expr.type == Exprent.EXPRENT_FUNCTION) {

      boolean ismmpp = true;

      if (expr.type == Exprent.EXPRENT_FUNCTION) {

        ismmpp = false;

        FunctionExprent fexpr = (FunctionExprent)expr;
        if (fexpr.getFuncType() >= FunctionExprent.FUNCTION_IMM && fexpr.getFuncType() <= FunctionExprent.FUNCTION_PPI) {
          if (fexpr.getLstOperands().get(0).type == Exprent.EXPRENT_FIELD) {
            ismmpp = true;
          }
        }
      }

      if (ismmpp) {
        varmap.removeAllFields();
      }
    }


    if (varassign != null) {

      Integer varindex = varassign.getIndex();

      if (varassign.getVersion() == 0) {
        // get next version
        Integer nextver = getNextFreeVersion(varindex, stat);

        // set version
        varassign.setVersion(nextver);

        // ssu graph
        ssuversions.createNode(new VarVersionPair(varindex, nextver));

        setCurrentVar(varmap, varindex, nextver);
      }
      else {
        if (calcLiveVars) {
          varMapToGraph(new VarVersionPair(varindex.intValue(), varassign.getVersion()), varmap);
        }
        setCurrentVar(varmap, varindex, varassign.getVersion());
      }
    }
    else if (expr.type == Exprent.EXPRENT_FUNCTION) { // MM or PP function
      FunctionExprent func = (FunctionExprent)expr;

      switch (func.getFuncType()) {
        case FunctionExprent.FUNCTION_IMM, FunctionExprent.FUNCTION_MMI, FunctionExprent.FUNCTION_IPP, FunctionExprent.FUNCTION_PPI -> {
          if (func.getLstOperands().get(0).type == Exprent.EXPRENT_VAR) {
            VarExprent var = (VarExprent)func.getLstOperands().get(0);
            Integer varindex = var.getIndex();
            VarVersionPair varpaar = new VarVersionPair(varindex.intValue(), var.getVersion());

            // ssu graph
            VarVersionPair phantomver = phantomppnodes.get(varpaar);
            if (phantomver == null) {
              // get next version
              Integer nextver = getNextFreeVersion(varindex, null);
              phantomver = new VarVersionPair(varindex, nextver);
              //ssuversions.createOrGetNode(phantomver);
              ssuversions.createNode(phantomver);

              VarVersionNode vernode = ssuversions.nodes.getWithKey(varpaar);

              FastSparseSet<Integer> vers = factory.spawnEmptySet();
              if (vernode.predecessors.size() == 1) {
                vers.add(vernode.predecessors.iterator().next().source.version);
              }
              else {
                for (VarVersionEdge edge : vernode.predecessors) {
                  vers.add(edge.source.predecessors.iterator().next().source.version);
                }
              }
              vers.add(nextver);
              createOrUpdatePhiNode(varpaar, vers, stat);
              phantomppnodes.put(varpaar, phantomver);
            }
            if (calcLiveVars) {
              varMapToGraph(varpaar, varmap);
            }
            setCurrentVar(varmap, varindex, var.getVersion());
          }
        }
      }
    }
    else if (expr.type == Exprent.EXPRENT_VAR) {

      VarExprent vardest = (VarExprent)expr;

      Integer varindex = vardest.getIndex();
      Integer current_vers = vardest.getVersion();

      FastSparseSet<Integer> vers = varmap.get(varindex);

      int cardinality = vers.getCardinality();
      if (cardinality == 1) { // size == 1
        if (current_vers != 0) {
          if (calcLiveVars) {
            varMapToGraph(new VarVersionPair(varindex, current_vers), varmap);
          }
          setCurrentVar(varmap, varindex, current_vers);
        }
        else {
          // split last version
          Integer usever = getNextFreeVersion(varindex, stat);

          // set version
          vardest.setVersion(usever);
          setCurrentVar(varmap, varindex, usever);

          // ssu graph
          Integer lastver = vers.iterator().next();
          VarVersionNode prenode = ssuversions.nodes.getWithKey(new VarVersionPair(varindex, lastver));
          VarVersionNode usenode = ssuversions.createNode(new VarVersionPair(varindex, usever));
          VarVersionEdge edge = new VarVersionEdge(VarVersionEdge.EDGE_GENERAL, prenode, usenode);
          prenode.addSuccessor(edge);
          usenode.addPredecessor(edge);
        }
      }
      else if (cardinality == 2) { // size > 1

        if (current_vers != 0) {
          if (calcLiveVars) {
            varMapToGraph(new VarVersionPair(varindex, current_vers), varmap);
          }
          setCurrentVar(varmap, varindex, current_vers);
        }
        else {
          // split version
          Integer usever = getNextFreeVersion(varindex, stat);
          // set version
          vardest.setVersion(usever);

          // ssu node
          ssuversions.createNode(new VarVersionPair(varindex, usever));

          setCurrentVar(varmap, varindex, usever);

          current_vers = usever;
        }

        createOrUpdatePhiNode(new VarVersionPair(varindex, current_vers), vers, stat);
      } // vers.size() == 0 means uninitialized variable, which is impossible
    }
  }

  private void createOrUpdatePhiNode(VarVersionPair phivar, FastSparseSet<Integer> vers, Statement stat) {

    FastSparseSet<Integer> versCopy = vers.getCopy();

    // take into account the corresponding mm/pp node if existing
    int ppvers = phantomppnodes.containsKey(phivar) ? phantomppnodes.get(phivar).version : -1;

    // ssu graph
    VarVersionNode phinode = ssuversions.nodes.getWithKey(phivar);
    List<VarVersionEdge> lstPreds = new ArrayList<>(phinode.predecessors);
    if (lstPreds.size() == 1) {
      // not yet a phi node
      VarVersionEdge edge = lstPreds.get(0);
      edge.source.removeSuccessor(edge);
      phinode.removePredecessor(edge);
    }
    else {
      for (VarVersionEdge edge : lstPreds) {
        int verssrc = edge.source.predecessors.iterator().next().source.version;
        if (!vers.contains(verssrc) && verssrc != ppvers) {
          edge.source.removeSuccessor(edge);
          phinode.removePredecessor(edge);
        }
        else {
          versCopy.remove(verssrc);
        }
      }
    }

    List<VarVersionNode> colnodes = new ArrayList<>();
    List<VarVersionPair> colpaars = new ArrayList<>();

    for (Integer ver : versCopy) {

      VarVersionNode prenode = ssuversions.nodes.getWithKey(new VarVersionPair(phivar.var, ver.intValue()));

      Integer tempver = getNextFreeVersion(phivar.var, stat);

      VarVersionNode tempnode = new VarVersionNode(phivar.var, tempver);

      colnodes.add(tempnode);
      colpaars.add(new VarVersionPair(phivar.var, tempver.intValue()));

      VarVersionEdge edge = new VarVersionEdge(VarVersionEdge.EDGE_GENERAL, prenode, tempnode);

      prenode.addSuccessor(edge);
      tempnode.addPredecessor(edge);


      edge = new VarVersionEdge(VarVersionEdge.EDGE_GENERAL, tempnode, phinode);
      tempnode.addSuccessor(edge);
      phinode.addPredecessor(edge);
    }

    ssuversions.addNodes(colnodes, colpaars);
  }

  private void varMapToGraph(VarVersionPair varpaar, SFormsFastMapDirect varmap) {

    VBStyleCollection<VarVersionNode, VarVersionPair> nodes = ssuversions.nodes;

    VarVersionNode node = nodes.getWithKey(varpaar);

    node.live = new SFormsFastMapDirect(varmap);
  }

  private Integer getNextFreeVersion(Integer var, Statement stat) {

    Integer nextver = lastversion.get(var);

    if (nextver == null) {
      nextver = 1;
    }
    else {
      nextver++;
    }
    lastversion.put(var, nextver);

    // save the first protected range, containing current statement
    if (stat != null) { // null iff phantom version
      Integer firstRangeId = getFirstProtectedRange(stat);
      if (firstRangeId != null) {
        mapVersionFirstRange.put(new VarVersionPair(var, nextver), firstRangeId);
      }
    }

    return nextver;
  }

  private void mergeInVarMaps(DirectNode node, DirectGraph dgraph) {


    SFormsFastMapDirect mapNew = new SFormsFastMapDirect();

    for (DirectNode pred : node.predecessors) {
      SFormsFastMapDirect mapOut = getFilteredOutMap(node.id, pred.id, dgraph, node.id);
      if (mapNew.isEmpty()) {
        mapNew = mapOut.getCopy();
      }
      else {
        mergeMaps(mapNew, mapOut);
      }
    }

    if (extraVarVersions.containsKey(node.id)) {
      SFormsFastMapDirect mapExtra = extraVarVersions.get(node.id);
      if (mapNew.isEmpty()) {
        mapNew = mapExtra.getCopy();
      }
      else {
        mergeMaps(mapNew, mapExtra);
      }
    }

    inVarVersions.put(node.id, mapNew);
  }

  private SFormsFastMapDirect getFilteredOutMap(String nodeid, String predid, DirectGraph dgraph, String destid) {

    SFormsFastMapDirect mapNew = new SFormsFastMapDirect();

    boolean isFinallyExit = dgraph.mapShortRangeFinallyPaths.containsKey(predid);

    if (nodeid.equals(dgraph.mapNegIfBranch.get(predid))) {
      if (outNegVarVersions.containsKey(predid)) {
        mapNew = outNegVarVersions.get(predid).getCopy();
      }
    }
    else if (outVarVersions.containsKey(predid)) {
      mapNew = outVarVersions.get(predid).getCopy();
    }

    if (isFinallyExit) {

      SFormsFastMapDirect mapNewTemp = mapNew.getCopy();

      SFormsFastMapDirect mapTrueSource = new SFormsFastMapDirect();

      String exceptionDest = dgraph.mapFinallyMonitorExceptionPathExits.get(predid);
      boolean isExceptionMonitorExit = (exceptionDest != null && !nodeid.equals(exceptionDest));

      HashSet<String> setLongPathWrapper = new HashSet<>();
      for (List<FinallyPathWrapper> lstwrapper : dgraph.mapLongRangeFinallyPaths.values()) {
        for (FinallyPathWrapper finwraplong : lstwrapper) {
          setLongPathWrapper.add(finwraplong.destination + "##" + finwraplong.source);
        }
      }

      for (FinallyPathWrapper finwrap : dgraph.mapShortRangeFinallyPaths.get(predid)) {
        SFormsFastMapDirect map;

        boolean recFinally = dgraph.mapShortRangeFinallyPaths.containsKey(finwrap.source);

        if (recFinally) {
          // recursion
          map = getFilteredOutMap(finwrap.entry, finwrap.source, dgraph, destid);
        }
        else {
          if (finwrap.entry.equals(dgraph.mapNegIfBranch.get(finwrap.source))) {
            map = outNegVarVersions.get(finwrap.source);
          }
          else {
            map = outVarVersions.get(finwrap.source);
          }
        }

        // false path?
        boolean isFalsePath;

        if (recFinally) {
          isFalsePath = !finwrap.destination.equals(nodeid);
        }
        else {
          isFalsePath = !setLongPathWrapper.contains(destid + "##" + finwrap.source);
        }

        if (isFalsePath) {
          mapNewTemp.complement(map);
        }
        else {
          if (mapTrueSource.isEmpty()) {
            if (map != null) {
              mapTrueSource = map.getCopy();
            }
          }
          else {
            mergeMaps(mapTrueSource, map);
          }
        }
      }

      if (isExceptionMonitorExit) {

        mapNew = mapTrueSource;
      }
      else {

        mapNewTemp.union(mapTrueSource);
        mapNew.intersection(mapNewTemp);

        if (!mapTrueSource.isEmpty() && !mapNew.isEmpty()) { // FIXME: what for??

          // replace phi versions with corresponding phantom ones
          HashMap<VarVersionPair, VarVersionPair> mapPhantom = phantomexitnodes.get(predid);
          if (mapPhantom == null) {
            mapPhantom = new HashMap<>();
          }

          SFormsFastMapDirect mapExitVar = mapNew.getCopy();
          mapExitVar.complement(mapTrueSource);

          for (Entry<Integer, FastSparseSet<Integer>> ent : mapExitVar.entryList()) {
            for (Integer version : ent.getValue()) {

              Integer varindex = ent.getKey();
              VarVersionPair exitvar = new VarVersionPair(varindex, version);
              FastSparseSet<Integer> newSet = mapNew.get(varindex);

              // remove the actual exit version
              newSet.remove(version);

              // get or create phantom version
              VarVersionPair phantomvar = mapPhantom.get(exitvar);
              if (phantomvar == null) {
                Integer newversion = getNextFreeVersion(exitvar.var, null);
                phantomvar = new VarVersionPair(exitvar.var, newversion.intValue());

                VarVersionNode exitnode = ssuversions.nodes.getWithKey(exitvar);
                VarVersionNode phantomnode = ssuversions.createNode(phantomvar);
                phantomnode.flags |= VarVersionNode.FLAG_PHANTOM_FIN_EXIT;

                VarVersionEdge edge = new VarVersionEdge(VarVersionEdge.EDGE_PHANTOM, exitnode, phantomnode);
                exitnode.addSuccessor(edge);
                phantomnode.addPredecessor(edge);

                mapPhantom.put(exitvar, phantomvar);
              }

              // add phantom version
              newSet.add(phantomvar.version);
            }
          }

          if (!mapPhantom.isEmpty()) {
            phantomexitnodes.put(predid, mapPhantom);
          }
        }
      }
    }

    return mapNew;
  }

  private static SFormsFastMapDirect mergeMaps(SFormsFastMapDirect mapTo, SFormsFastMapDirect map2) {

    if (map2 != null && !map2.isEmpty()) {
      mapTo.union(map2);
    }

    return mapTo;
  }

  private static boolean mapsEqual(SFormsFastMapDirect map1, SFormsFastMapDirect map2) {

    if (map1 == null) {
      return map2 == null;
    }
    else if (map2 == null) {
      return false;
    }

    if (map1.size() != map2.size()) {
      return false;
    }

    for (Entry<Integer, FastSparseSet<Integer>> ent2 : map2.entryList()) {
      if (!Objects.equals(map1.get(ent2.getKey()), ent2.getValue())) {
        return false;
      }
    }

    return true;
  }


  private void setCurrentVar(SFormsFastMapDirect varmap, Integer var, Integer vers) {
    FastSparseSet<Integer> set = factory.spawnEmptySet();
    set.add(vers);
    varmap.put(var, set);
  }

  private void setCatchMaps(Statement stat, DirectGraph dgraph, FlattenStatementsHelper flatthelper) {

    SFormsFastMapDirect map;

    switch (stat.type) {
      case CATCH_ALL, TRY_CATCH -> {
        List<VarExprent> lstVars;
        if (stat.type == StatementType.CATCH_ALL) {
          lstVars = ((CatchAllStatement)stat).getVars();
        }
        else {
          lstVars = ((CatchStatement)stat).getVars();
        }

        for (int i = 1; i < stat.getStats().size(); i++) {
          int varindex = lstVars.get(i - 1).getIndex();
          int version = getNextFreeVersion(varindex, stat); // == 1

          map = new SFormsFastMapDirect();
          setCurrentVar(map, varindex, version);

          extraVarVersions.put(dgraph.nodes.getWithKey(flatthelper.getMapDestinationNodes().get(stat.getStats().get(i).id)[0]).id, map);
          //ssuversions.createOrGetNode(new VarVersionPair(varindex, version));
          ssuversions.createNode(new VarVersionPair(varindex, version));
        }
      }
    }

    for (Statement st : stat.getStats()) {
      setCatchMaps(st, dgraph, flatthelper);
    }
  }

  private SFormsFastMapDirect createFirstMap(StructMethod mt, RootStatement root) {
    boolean thisvar = !mt.hasModifier(CodeConstants.ACC_STATIC);

    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

    int paramcount = md.params.length + (thisvar ? 1 : 0);

    int varindex = 0;
    SFormsFastMapDirect map = new SFormsFastMapDirect();
    for (int i = 0; i < paramcount; i++) {
      int version = getNextFreeVersion(varindex, root); // == 1

      FastSparseSet<Integer> set = factory.spawnEmptySet();
      set.add(version);
      map.put(varindex, set);
      ssuversions.createNode(new VarVersionPair(varindex, version));

      if (thisvar) {
        if (i == 0) {
          varindex++;
        }
        else {
          varindex += md.params[i - 1].getStackSize();
        }
      }
      else {
        varindex += md.params[i].getStackSize();
      }
    }

    return map;
  }

  private static Integer getFirstProtectedRange(Statement stat) {

    while (true) {
      Statement parent = stat.getParent();

      if (parent == null) {
        break;
      }

      if (parent.type == StatementType.CATCH_ALL ||
          parent.type == StatementType.TRY_CATCH) {
        if (parent.getFirst() == stat) {
          return parent.id;
        }
      }
      else if (parent.type == StatementType.SYNCHRONIZED) {
        if (((SynchronizedStatement)parent).getBody() == stat) {
          return parent.id;
        }
      }

      stat = parent;
    }

    return null;
  }

  public VarVersionsGraph getSsuversions() {
    return ssuversions;
  }

  public SFormsFastMapDirect getLiveVarVersionsMap(VarVersionPair varpaar) {


    VarVersionNode node = ssuversions.nodes.getWithKey(varpaar);
    if (node != null) {
      return node.live;
    }

    return null;
  }

  public HashMap<VarVersionPair, Integer> getMapVersionFirstRange() {
    return mapVersionFirstRange;
  }

  public HashMap<Integer, Integer> getMapFieldVars() {
    return mapFieldVars;
  }
}
