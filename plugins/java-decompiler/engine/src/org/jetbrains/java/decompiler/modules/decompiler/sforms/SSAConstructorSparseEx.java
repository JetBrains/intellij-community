// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.sforms;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper.FinallyPathWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.SFormsFastMapDirect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

public class SSAConstructorSparseEx {

  // node id, var, version
  private final HashMap<String, SFormsFastMapDirect> inVarVersions = new HashMap<>();

  // node id, var, version (direct branch)
  private final HashMap<String, SFormsFastMapDirect> outVarVersions = new HashMap<>();

  // node id, var, version (negative branch)
  private final HashMap<String, SFormsFastMapDirect> outNegVarVersions = new HashMap<>();

  // node id, var, version
  private final HashMap<String, SFormsFastMapDirect> extraVarVersions = new HashMap<>();

  // (var, version), version
  private final HashMap<VarVersionPair, FastSparseSet<Integer>> phi = new HashMap<>();

  // var, version
  private final HashMap<Integer, Integer> lastversion = new HashMap<>();

  // set factory
  private FastSparseSetFactory<Integer> factory;

  public void splitVariables(RootStatement root, StructMethod mt) {

    FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
    DirectGraph dgraph = flatthelper.buildDirectGraph(root);

    // try {
    // DotExporter.toDotFile(dgraph, new File("c:\\Temp\\gr12_my.dot"));
    // } catch(Exception ex) {ex.printStackTrace();}

    HashSet<Integer> setInit = new HashSet<>();
    for (int i = 0; i < 64; i++) {
      setInit.add(i);
    }
    factory = new FastSparseSetFactory<>(setInit);

    SFormsFastMapDirect firstmap = createFirstMap(mt);
    extraVarVersions.put(dgraph.first.id, firstmap);

    setCatchMaps(root, dgraph, flatthelper);

    HashSet<String> updated = new HashSet<>();
    do {
      // System.out.println("~~~~~~~~~~~~~ \r\n"+root.toJava());
      ssaStatements(dgraph, updated);
      // System.out.println("~~~~~~~~~~~~~ \r\n"+root.toJava());
    }
    while (!updated.isEmpty());
  }

  private void ssaStatements(DirectGraph dgraph, HashSet<String> updated) {

    // try {
    // DotExporter.toDotFile(dgraph, new File("c:\\Temp\\gr1_my.dot"));
    // } catch(Exception ex) {ex.printStackTrace();}

    for (DirectNode node : dgraph.nodes) {

      //			if (node.id.endsWith("_inc")) {
      //				System.out.println();
      //
      //				try {
      //					DotExporter.toDotFile(dgraph, new File("c:\\Temp\\gr1_my.dot"));
      //				} catch (Exception ex) {
      //					ex.printStackTrace();
      //				}
      //			}

      updated.remove(node.id);
      mergeInVarMaps(node, dgraph);

      SFormsFastMapDirect varmap = inVarVersions.get(node.id);
      varmap = new SFormsFastMapDirect(varmap);

      SFormsFastMapDirect[] varmaparr = new SFormsFastMapDirect[]{varmap, null};

      if (node.exprents != null) {
        for (Exprent expr : node.exprents) {
          processExprent(expr, varmaparr);
        }
      }

      if (varmaparr[1] == null) {
        varmaparr[1] = varmaparr[0];
      }

      boolean this_updated = !mapsEqual(varmaparr[0], outVarVersions.get(node.id))
                             || (outNegVarVersions.containsKey(node.id) && !mapsEqual(varmaparr[1], outNegVarVersions.get(node.id)));

      if (this_updated) {
        outVarVersions.put(node.id, varmaparr[0]);
        if (dgraph.mapNegIfBranch.containsKey(node.id)) {
          outNegVarVersions.put(node.id, varmaparr[1]);
        }

        for (DirectNode nd : node.succs) {
          updated.add(nd.id);
        }
      }
    }
  }

  private void processExprent(Exprent expr, SFormsFastMapDirect[] varmaparr) {

    if (expr == null) {
      return;
    }

    VarExprent varassign = null;
    boolean finished = false;

    switch (expr.type) {
      case Exprent.EXPRENT_ASSIGNMENT:
        AssignmentExprent assexpr = (AssignmentExprent)expr;
        if (assexpr.getCondType() == AssignmentExprent.CONDITION_NONE) {
          Exprent dest = assexpr.getLeft();
          if (dest.type == Exprent.EXPRENT_VAR) {
            varassign = (VarExprent)dest;
          }
        }
        break;
      case Exprent.EXPRENT_FUNCTION:
        FunctionExprent func = (FunctionExprent)expr;
        switch (func.getFuncType()) {
          case FunctionExprent.FUNCTION_IIF:
            processExprent(func.getLstOperands().get(0), varmaparr);

            SFormsFastMapDirect varmapFalse;
            if (varmaparr[1] == null) {
              varmapFalse = new SFormsFastMapDirect(varmaparr[0]);
            }
            else {
              varmapFalse = varmaparr[1];
              varmaparr[1] = null;
            }

            processExprent(func.getLstOperands().get(1), varmaparr);

            SFormsFastMapDirect[] varmaparrNeg = new SFormsFastMapDirect[]{varmapFalse, null};
            processExprent(func.getLstOperands().get(2), varmaparrNeg);

            mergeMaps(varmaparr[0], varmaparrNeg[0]);
            varmaparr[1] = null;

            finished = true;
            break;
          case FunctionExprent.FUNCTION_CADD:
            processExprent(func.getLstOperands().get(0), varmaparr);

            SFormsFastMapDirect[] varmaparrAnd = new SFormsFastMapDirect[]{new SFormsFastMapDirect(varmaparr[0]), null};

            processExprent(func.getLstOperands().get(1), varmaparrAnd);

            // false map
            varmaparr[1] = mergeMaps(varmaparr[varmaparr[1] == null ? 0 : 1], varmaparrAnd[varmaparrAnd[1] == null ? 0 : 1]);
            // true map
            varmaparr[0] = varmaparrAnd[0];

            finished = true;
            break;
          case FunctionExprent.FUNCTION_COR:
            processExprent(func.getLstOperands().get(0), varmaparr);

            SFormsFastMapDirect[] varmaparrOr =
              new SFormsFastMapDirect[]{new SFormsFastMapDirect(varmaparr[varmaparr[1] == null ? 0 : 1]), null};

            processExprent(func.getLstOperands().get(1), varmaparrOr);

            // false map
            varmaparr[1] = varmaparrOr[varmaparrOr[1] == null ? 0 : 1];
            // true map
            varmaparr[0] = mergeMaps(varmaparr[0], varmaparrOr[0]);

            finished = true;
        }
    }

    if (finished) {
      return;
    }

    List<Exprent> lst = expr.getAllExprents();
    lst.remove(varassign);

    for (Exprent ex : lst) {
      processExprent(ex, varmaparr);
    }

    SFormsFastMapDirect varmap = varmaparr[0];

    if (varassign != null) {

      Integer varindex = varassign.getIndex();

      if (varassign.getVersion() == 0) {
        // get next version
        Integer nextver = getNextFreeVersion(varindex);

        // set version
        varassign.setVersion(nextver);

        setCurrentVar(varmap, varindex, nextver);
      }
      else {
        setCurrentVar(varmap, varindex, varassign.getVersion());
      }
    }
    else if (expr.type == Exprent.EXPRENT_VAR) {

      VarExprent vardest = (VarExprent)expr;
      Integer varindex = vardest.getIndex();
      FastSparseSet<Integer> vers = varmap.get(varindex);

      int cardinality = vers.getCardinality();
      if (cardinality == 1) { // == 1
        // set version
        Integer it = vers.iterator().next();
        vardest.setVersion(it);
      }
      else if (cardinality == 2) { // size > 1
        Integer current_vers = vardest.getVersion();

        VarVersionPair currpaar = new VarVersionPair(varindex, current_vers);
        if (current_vers != 0 && phi.containsKey(currpaar)) {
          setCurrentVar(varmap, varindex, current_vers);
          // update phi node
          phi.get(currpaar).union(vers);
        }
        else {
          // increase version
          Integer nextver = getNextFreeVersion(varindex);
          // set version
          vardest.setVersion(nextver);

          setCurrentVar(varmap, varindex, nextver);
          // create new phi node
          phi.put(new VarVersionPair(varindex, nextver), vers);
        }
      } // 0 means uninitialized variable, which is impossible
    }
  }

  private Integer getNextFreeVersion(Integer var) {
    Integer nextver = lastversion.get(var);
    if (nextver == null) {
      nextver = 1;
    }
    else {
      nextver++;
    }
    lastversion.put(var, nextver);
    return nextver;
  }

  private void mergeInVarMaps(DirectNode node, DirectGraph dgraph) {

    SFormsFastMapDirect mapNew = new SFormsFastMapDirect();

    for (DirectNode pred : node.preds) {
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

    if (nodeid.equals(dgraph.mapNegIfBranch.get(predid))) {
      if (outNegVarVersions.containsKey(predid)) {
        mapNew = outNegVarVersions.get(predid).getCopy();
      }
    }
    else if (outVarVersions.containsKey(predid)) {
      mapNew = outVarVersions.get(predid).getCopy();
    }

    boolean isFinallyExit = dgraph.mapShortRangeFinallyPaths.containsKey(predid);

    if (isFinallyExit && !mapNew.isEmpty()) {

      SFormsFastMapDirect mapNewTemp = mapNew.getCopy();

      SFormsFastMapDirect mapTrueSource = new SFormsFastMapDirect();

      String exceptionDest = dgraph.mapFinallyMonitorExceptionPathExits.get(predid);
      boolean isExceptionMonitorExit = (exceptionDest != null && !nodeid.equals(exceptionDest));

      HashSet<String> setLongPathWrapper = new HashSet<>();
      for (FinallyPathWrapper finwraplong : dgraph.mapLongRangeFinallyPaths.get(predid)) {
        setLongPathWrapper.add(finwraplong.destination + "##" + finwraplong.source);
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

        SFormsFastMapDirect oldInMap = inVarVersions.get(nodeid);
        if (oldInMap != null) {
          mapNewTemp.union(oldInMap);
        }

        mapNew.intersection(mapNewTemp);
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
      if (!InterpreterUtil.equalObjects(map1.get(ent2.getKey()), ent2.getValue())) {
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
      case Statement.TYPE_CATCHALL:
      case Statement.TYPE_TRYCATCH:

        List<VarExprent> lstVars;
        if (stat.type == Statement.TYPE_CATCHALL) {
          lstVars = ((CatchAllStatement)stat).getVars();
        }
        else {
          lstVars = ((CatchStatement)stat).getVars();
        }

        for (int i = 1; i < stat.getStats().size(); i++) {
          int varindex = lstVars.get(i - 1).getIndex();
          int version = getNextFreeVersion(varindex); // == 1

          map = new SFormsFastMapDirect();
          setCurrentVar(map, varindex, version);

          extraVarVersions.put(dgraph.nodes.getWithKey(flatthelper.getMapDestinationNodes().get(stat.getStats().get(i).id)[0]).id, map);
        }
    }

    for (Statement st : stat.getStats()) {
      setCatchMaps(st, dgraph, flatthelper);
    }
  }

  private SFormsFastMapDirect createFirstMap(StructMethod mt) {
    boolean thisvar = !mt.hasModifier(CodeConstants.ACC_STATIC);

    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

    int paramcount = md.params.length + (thisvar ? 1 : 0);

    int varindex = 0;
    SFormsFastMapDirect map = new SFormsFastMapDirect();
    for (int i = 0; i < paramcount; i++) {
      int version = getNextFreeVersion(varindex); // == 1

      FastSparseSet<Integer> set = factory.spawnEmptySet();
      set.add(version);
      map.put(varindex, set);

      if (thisvar) {
        if (i == 0) {
          varindex++;
        }
        else {
          varindex += md.params[i - 1].stackSize;
        }
      }
      else {
        varindex += md.params[i].stackSize;
      }
    }

    return map;
  }

  public HashMap<VarVersionPair, FastSparseSet<Integer>> getPhi() {
    return phi;
  }
}
