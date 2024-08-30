// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.LoopType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute.LocalVariable;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericType;
import org.jetbrains.java.decompiler.util.StatementIterator;

import java.util.*;
import java.util.Map.Entry;

public class VarDefinitionHelper {

  private final HashMap<Integer, Statement> mapVarDefStatements;

  // statement.id, defined vars
  private final HashMap<Integer, HashSet<Integer>> mapStatementVars;

  private final HashSet<Integer> implDefVars;

  private final VarProcessor varproc;

  private final Statement root;
  private final StructMethod mt;

  public VarDefinitionHelper(Statement root, StructMethod mt, VarProcessor varproc) {

    mapVarDefStatements = new HashMap<>();
    mapStatementVars = new HashMap<>();
    implDefVars = new HashSet<>();

    this.varproc = varproc;
    this.root = root;
    this.mt = mt;

    VarNamesCollector vc = varproc.getVarNamesCollector();

    boolean thisvar = !mt.hasModifier(CodeConstants.ACC_STATIC);

    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

    int paramcount = 0;
    if (thisvar) {
      paramcount = 1;
    }
    paramcount += md.params.length;

    // method parameters are implicitly defined
    int varindex = 0;
    for (int i = 0; i < paramcount; i++) {
      implDefVars.add(varindex);
      VarVersionPair vpp = new VarVersionPair(varindex, 0);
      varproc.setVarName(vpp, vc.getFreeName(varindex));

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

    if (thisvar) {
      StructClass current_class = (StructClass)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS);

      varproc.getThisVars().put(new VarVersionPair(0, 0), current_class.qualifiedName);
      varproc.setVarName(new VarVersionPair(0, 0), "this");
      vc.addName("this");
    }

    mergeVars(root);

    // catch variables are implicitly defined
    LinkedList<Statement> stack = new LinkedList<>();
    stack.add(root);

    while (!stack.isEmpty()) {
      Statement st = stack.removeFirst();

      List<VarExprent> lstVars = null;
      if (st.type == StatementType.CATCH_ALL) {
        lstVars = ((CatchAllStatement)st).getVars();
      }
      else if (st.type == StatementType.TRY_CATCH) {
        lstVars = ((CatchStatement)st).getVars();
      }

      if (lstVars != null) {
        for (VarExprent var : lstVars) {
          implDefVars.add(var.getIndex());
          varproc.setVarName(new VarVersionPair(var), vc.getFreeName(var.getIndex()));
          var.setDefinition(true);
        }
      }

      stack.addAll(st.getStats());
    }

    initStatement(root);
  }

  public void setVarDefinitions() {
    VarNamesCollector vc = varproc.getVarNamesCollector();

    for (Entry<Integer, Statement> en : mapVarDefStatements.entrySet()) {
      Statement stat = en.getValue();
      Integer index = en.getKey();

      if (implDefVars.contains(index)) {
        // already implicitly defined
        continue;
      }

      varproc.setVarName(new VarVersionPair(index.intValue(), 0), vc.getFreeName(index));

      // special case for
      if (stat.type == StatementType.DO) {
        DoStatement dstat = (DoStatement)stat;
        if (dstat.getLoopType() == LoopType.FOR) {

          if (dstat.getInitExprent() != null && setDefinition(dstat.getInitExprent(), index)) {
            continue;
          }
          else {
            List<Exprent> lstSpecial = Arrays.asList(dstat.getConditionExprent(), dstat.getIncExprent());
            for (VarExprent var : getAllVars(lstSpecial)) {
              if (var.getIndex() == index) {
                stat = stat.getParent();
                break;
              }
            }
          }
        }
        else if (dstat.getLoopType() == DoStatement.LoopType.FOREACH) {
          if (dstat.getInitExprent() != null && dstat.getInitExprent().type == Exprent.EXPRENT_VAR) {
            VarExprent var = (VarExprent)dstat.getInitExprent();
            if (var.getIndex() == index.intValue()) {
              var.setDefinition(true);
              continue;
            }
          }
        }
      }

      Statement first = findFirstBlock(stat, index);

      List<Exprent> lst;
      if (first == null) {
        lst = stat.getVarDefinitions();
      }
      else if (first.getExprents() == null) {
        lst = first.getVarDefinitions();
      }
      else {
        lst = first.getExprents();
      }

      boolean defset = false;

      // search for the first assignment to var [index]
      int addindex = 0;
      for (Exprent expr : lst) {
        if (setDefinition(expr, index)) {
          defset = true;
          break;
        }
        else {
          boolean foundvar = false;
          for (Exprent exp : expr.getAllExprents(true)) {
            if (exp.type == Exprent.EXPRENT_VAR && ((VarExprent)exp).getIndex() == index) {
              foundvar = true;
              break;
            }
          }
          if (foundvar) {
            break;
          }
        }
        addindex++;
      }

      if (!defset) {
        VarExprent var = new VarExprent(index, varproc.getVarType(new VarVersionPair(index.intValue(), 0)), varproc);
        var.setDefinition(true);

        LocalVariable lvt = findLVT(index.intValue(), stat);
        if (lvt != null) {
          var.setLVT(lvt);
        }

        lst.add(addindex, var);
      }
    }

    mergeVars(root);
    propogateLVTs(root);
  }


  // *****************************************************************************
  // private methods
  // *****************************************************************************

  private static LocalVariable findLVT(int index, Statement stat) {
    if (stat.getExprents() == null) {
      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement) {
          LocalVariable lvt = findLVT(index, (Statement)obj);
          if (lvt != null) {
            return lvt;
          }
        }
        else if (obj instanceof Exprent) {
          LocalVariable lvt = findLVT(index, (Exprent)obj);
          if (lvt != null) {
            return lvt;
          }
        }
      }
    }
    else {
      for (Exprent exp : stat.getExprents()) {
        LocalVariable lvt = findLVT(index, exp);
        if (lvt != null) {
          return lvt;
        }
      }
    }
    return null;
  }

  private static LocalVariable findLVT(int index, Exprent exp) {
    for (Exprent e: exp.getAllExprents(false)) {
      LocalVariable lvt = findLVT(index, e);
      if (lvt != null) {
        return lvt;
      }
    }

    if (exp.type != Exprent.EXPRENT_VAR) {
      return null;
    }

    VarExprent var = (VarExprent)exp;
    return var.getIndex() == index ? var.getLVT() : null;
  }

  private Statement findFirstBlock(Statement stat, Integer varindex) {

    LinkedList<Statement> stack = new LinkedList<>();
    stack.add(stat);

    while (!stack.isEmpty()) {
      Statement st = stack.remove(0);

      if (stack.isEmpty() || mapStatementVars.get(st.id).contains(varindex)) {

        if (st.isLabeled() && !stack.isEmpty()) {
          return st;
        }

        if (st.getExprents() != null) {
          return st;
        }
        else {
          stack.clear();

          switch (st.type) {
            case SEQUENCE -> stack.addAll(0, st.getStats());
            case IF, ROOT, SWITCH, SYNCHRONIZED -> stack.add(st.getFirst());
            default -> {
              return st;
            }
          }
        }
      }
    }

    return null;
  }

  private Set<Integer> initStatement(Statement stat) {

    HashMap<Integer, Integer> mapCount = new HashMap<>();

    List<VarExprent> condlst;

    if (stat.getExprents() == null) {

      // recurse on children statements
      List<Integer> childVars = new ArrayList<>();
      List<Exprent> currVars = new ArrayList<>();

      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement st) {
          childVars.addAll(initStatement(st));

          if (st.type == StatementType.DO) {
            DoStatement dost = (DoStatement)st;
            if (dost.getLoopType() != LoopType.FOR &&
                dost.getLoopType() != LoopType.FOREACH &&
                dost.getLoopType() != LoopType.DO) {
              currVars.add(dost.getConditionExprent());
            }
          }
          else if (st.type == StatementType.CATCH_ALL) {
            CatchAllStatement fin = (CatchAllStatement)st;
            if (fin.isFinally() && fin.getMonitor() != null) {
              currVars.add(fin.getMonitor());
            }
          }
        }
        else if (obj instanceof Exprent) {
          currVars.add((Exprent)obj);
        }
      }

      // children statements
      for (Integer index : childVars) {
        Integer count = mapCount.get(index);
        if (count == null) {
          count = 0;
        }
        mapCount.put(index, count + 1);
      }

      condlst = getAllVars(currVars);
    }
    else {
      condlst = getAllVars(stat.getExprents());
    }

    // this statement
    for (VarExprent var : condlst) {
      mapCount.put(var.getIndex(), 2);
    }


    HashSet<Integer> set = new HashSet<>(mapCount.keySet());

    // put all variables defined in this statement into the set
    for (Entry<Integer, Integer> en : mapCount.entrySet()) {
      if (en.getValue() > 1) {
        mapVarDefStatements.put(en.getKey(), stat);
      }
    }

    mapStatementVars.put(stat.id, set);

    return set;
  }

  private static List<VarExprent> getAllVars(List<Exprent> lst) {

    List<VarExprent> res = new ArrayList<>();
    List<Exprent> listTemp = new ArrayList<>();

    for (Exprent expr : lst) {
      listTemp.addAll(expr.getAllExprents(true));
      listTemp.add(expr);
    }

    for (Exprent exprent : listTemp) {
      if (exprent.type == Exprent.EXPRENT_VAR) {
        res.add((VarExprent)exprent);
      }
    }

    return res;
  }

  private static boolean setDefinition(Exprent expr, Integer index) {
    if (expr.type == Exprent.EXPRENT_ASSIGNMENT) {
      Exprent left = ((AssignmentExprent)expr).getLeft();
      if (left.type == Exprent.EXPRENT_VAR) {
        VarExprent var = (VarExprent)left;
        if (var.getIndex() == index) {
          var.setDefinition(true);
          return true;
        }
      }
    }
    return false;
  }

  private void populateTypeBounds() {
    Map<VarVersionPair, VarType> mapExprentMinTypes = varproc.getVarVersions().getTypeProcessor().getMinExprentTypes();
    Map<VarVersionPair, VarType> mapExprentMaxTypes = varproc.getVarVersions().getTypeProcessor().getMaxExprentTypes();
    LinkedList<Statement> stack = new LinkedList<>();
    stack.add(root);

    while (!stack.isEmpty()) {
      Statement st = stack.removeFirst();

      if (st.getExprents() != null) {
        LinkedList<Exprent> exps = new LinkedList<>(st.getExprents());
        while (!exps.isEmpty()) {
          Exprent exp = exps.removeFirst();

          switch (exp.type) {
            case Exprent.EXPRENT_INVOCATION:
            case Exprent.EXPRENT_FIELD:
            case Exprent.EXPRENT_EXIT:
              Exprent instance = null;
              String target = null;
              if (exp.type == Exprent.EXPRENT_INVOCATION) {
                instance = ((InvocationExprent)exp).getInstance();
                target = ((InvocationExprent)exp).getClassName();
              } else if (exp.type == Exprent.EXPRENT_FIELD) {
                instance = ((FieldExprent)exp).getInstance();
                target = ((FieldExprent)exp).getClassname();
              } else { //exp.type == Exprent.EXPRENT_EXIT
                ExitExprent exit = (ExitExprent)exp;
                if (exit.getExitType() == ExitExprent.EXIT_RETURN) {
                  instance = exit.getValue();
                  target = exit.getRetType().getValue();
                }
              }

              if ("java/lang/Object".equals(target))
                  continue; //This is dirty, but if we don't then too many things become object...

              if (instance != null && instance.type == Exprent.EXPRENT_VAR) {
                VarVersionPair key = ((VarExprent)instance).getVarVersionPair();
                VarType newType = new VarType(CodeConstants.TYPE_OBJECT, 0, target);
                VarType oldMin = mapExprentMinTypes.get(key);
                VarType oldMax = mapExprentMaxTypes.get(key);

                /* Everything goes to Object with this... Need a better filter?
                if (!newType.equals(oldMin)) {
                  if (oldMin != null && oldMin.type == CodeConstants.TYPE_OBJECT) {
                    // If the old min is an instanceof the new target, EXA: ArrayList -> List
                    if (DecompilerContext.getStructContext().instanceOf(oldMin.value, newType.value))
                      mapExprentMinTypes.put(key, newType);
                  } else
                    mapExprentMinTypes.put(key, newType);
                }
                */

                if (!newType.equals(oldMax)) {
                  if (oldMax != null && oldMax.getType() == CodeConstants.TYPE_OBJECT) {
                    // If the old min is an instanceof the new target, EXA: List -> ArrayList
                    if (DecompilerContext.getStructContext().instanceOf(newType.getValue(), oldMax.getValue()))
                      mapExprentMaxTypes.put(key, newType);
                  } else
                    mapExprentMaxTypes.put(key, newType);
                }
              }

              break;
            default:
              exps.addAll(exp.getAllExprents());
          }
        }
      }

      stack.addAll(st.getStats());
    }
  }

  private void mergeVars(Statement stat) {
    Map<Integer, VarVersionPair> parent = new HashMap<>(); // Always empty dua!
    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

    int index = 0;
    if (!mt.hasModifier(CodeConstants.ACC_STATIC)) {
      parent.put(index, new VarVersionPair(index++, 0));
    }

    for (VarType var : md.params) {
      parent.put(index, new VarVersionPair(index, 0));
      index += var.getStackSize();
    }

    populateTypeBounds();

    Map<VarVersionPair, VarVersionPair> blacklist = new HashMap<>();
    VPPEntry remap = mergeVars(stat, parent, new HashMap<>(), blacklist);
    while (remap != null) {
      //System.out.println("Remapping: " + remap.getKey() + " -> " + remap.getValue());
      if (!remapVar(stat, remap.getKey(), remap.getValue())) {
        blacklist.put(remap.getKey(), remap.getValue());
      }
      remap = mergeVars(stat, parent, new HashMap<>(), blacklist);
    }
  }


  private VPPEntry mergeVars(Statement stat, Map<Integer, VarVersionPair> parent, Map<Integer, VarVersionPair> leaked, Map<VarVersionPair, VarVersionPair> blacklist) {
    Map<Integer, VarVersionPair> this_vars = new HashMap<>();
    if (!parent.isEmpty())
      this_vars.putAll(parent);

    if (!stat.getVarDefinitions().isEmpty()) {
      for (int x = 0; x < stat.getVarDefinitions().size(); x++) {
        Exprent exp = stat.getVarDefinitions().get(x);
        if (exp.type == Exprent.EXPRENT_VAR) {
          VarExprent var = (VarExprent)exp;
          int index = varproc.getVarOriginalIndex(var.getIndex());
          if (this_vars.containsKey(index)) {
            stat.getVarDefinitions().remove(x);
            return new VPPEntry(var, this_vars.get(index));
          }
          this_vars.put(index, new VarVersionPair(var));
          leaked.put(index, new VarVersionPair(var));
        }
      }
    }

    Map<Integer, VarVersionPair> scoped = switch (stat.type) { // These are the type of statements that leak vars
      case BASIC_BLOCK, GENERAL, ROOT, SEQUENCE -> leaked;
      default -> null;
    };

    if (stat.getExprents() == null) {
      List<Object> objs = stat.getSequentialObjects();
      for (int i = 0; i < objs.size(); i++) {
        Object obj = objs.get(i);
        if (obj instanceof Statement st) {

          //Map<VarVersionPair, VarVersionPair> blacklist_n = new HashMap<VarVersionPair, VarVersionPair>();
          Map<Integer, VarVersionPair> leaked_n = new HashMap<>();
          VPPEntry remap = mergeVars(st, this_vars, leaked_n, blacklist);

          if (remap != null) {
            return remap;
          }
          /* TODO: See if we can optimize and only go up till needed.
          while (remap != null) {
            System.out.println("Remapping: " + remap.getKey() + " -> " + remap.getValue());
            VarVersionPair var = parent.get(varproc.getRemapped(remap.getValue().var));
            if (remap.getValue().equals(var)) { //Drill up to original declaration.
              return remap;
            }
            if (!remapVar(stat, remap.getKey(), remap.getValue())) {
              blacklist_n.put(remap.getKey(), remap.getValue());
            }
            leaked_n.clear();
            remap = mergeVars(st, this_vars, leaked_n, blacklist_n);
          }
          */

          if (!leaked_n.isEmpty()) {
            if (stat.type == Statement.StatementType.IF) {
              IfStatement ifst = (IfStatement)stat;
              if (obj == ifst.getIfstat() || obj == ifst.getElsestat()) {
                leaked_n.clear(); // Force no leaking at the end of if blocks
                // We may need to do this for Switches as well.. But havent run into that issue yet...
              }
              else if (obj == ifst.getFirst()) {
                leaked.putAll(leaked_n); //First is outside the scope so leak!
              }
            } else if (stat.type == Statement.StatementType.SWITCH ||
                       stat.type == Statement.StatementType.SYNCHRONIZED) {
              if (obj == stat.getFirst()) {
                leaked.putAll(leaked_n); //First is outside the scope so leak!
              }
              else {
                leaked_n.clear();
              }
            }
            else if (stat.type == Statement.StatementType.TRY_CATCH ||
              stat.type == Statement.StatementType.CATCH_ALL) {
              leaked_n.clear(); // Catches can't leak anything mwhahahahah!
            }
            this_vars.putAll(leaked_n);
          }
        }
        else if (obj instanceof Exprent) {
          VPPEntry ret = processExprent((Exprent)obj, this_vars, scoped, blacklist);
          if (ret != null && isVarReadFirst(ret.getValue(), stat, i + 1)) {
            return ret;
          }
        }
      }
    }
    else {
      List<Exprent> exps = stat.getExprents();
      for (int i = 0; i < exps.size(); i++) {
        VPPEntry ret = processExprent(exps.get(i), this_vars, scoped, blacklist);
        if (ret != null && !isVarReadFirst(ret.getValue(), stat, i + 1)) {
          return ret;
        }
      }
    }
    return null; // We made it with no remaps!!!!!!!
  }

  private VPPEntry processExprent(Exprent exp, Map<Integer, VarVersionPair> this_vars, Map<Integer, VarVersionPair> leaked, Map<VarVersionPair, VarVersionPair> blacklist) {
    VarExprent var = null;

    if (exp.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent ass = (AssignmentExprent)exp;
      if (ass.getLeft().type != Exprent.EXPRENT_VAR) {
        return null;
      }

      var = (VarExprent)ass.getLeft();
    }
    else if (exp.type == Exprent.EXPRENT_VAR) {
      var = (VarExprent)exp;
    }

    if (var == null) {
      return null;
    }

    if (!var.isDefinition()) {
      return null;
    }

    int index = varproc.getVarOriginalIndex(var.getIndex());
    VarVersionPair new_ = this_vars.get(index);
    if (new_ != null) {
      VarVersionPair old = new VarVersionPair(var);
      VarVersionPair black = blacklist.get(old);
      if (black == null || !black.equals(new_)) {
        return new VPPEntry(var, this_vars.get(index));
      }
    }
    this_vars.put(index, new VarVersionPair(var));

    if (leaked != null) {
      leaked.put(index, new VarVersionPair(var));
    }

    return null;
  }

  private boolean remapVar(Statement stat, VarVersionPair from, VarVersionPair to) {
    if (from.equals(to))
      throw new IllegalArgumentException("Shit went wrong: " + from);
    boolean success = false;
    if (stat.getExprents() == null) {
      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement) {
          success |= remapVar((Statement)obj, from, to);
        }
        else if (obj instanceof Exprent) {
          if (remapVar((Exprent)obj, from, to)) {
            success = true;
          }
        }
      }
    }
    else {
      boolean remapped = false;
      for (int x = 0; x < stat.getExprents().size(); x++) {
        Exprent exp = stat.getExprents().get(x);
        if (remapVar(exp, from, to)) {
          remapped = true;
          if (exp.type == Exprent.EXPRENT_VAR) {
            if (!((VarExprent)exp).isDefinition()) {
              stat.getExprents().remove(x);
              x--;
            }
          }
        }
      }
      success |= remapped;
    }

    if (success) {
      Iterator<Exprent> itr = stat.getVarDefinitions().iterator();
      while (itr.hasNext()) {
        Exprent exp = itr.next();
        if (exp.type == Exprent.EXPRENT_VAR) {
          VarExprent var = (VarExprent)exp;
          if (from.equals(var.getVarVersionPair())) {
            itr.remove();
          }
          else if (to.var == var.getIndex() && to.version == var.getVersion()) {
            VarType merged = getMergedType(from, to);

            if (merged == null) { // Something went wrong.. This SHOULD be non-null
              continue;
            }

            var.setVarType(merged);
          }
        }
      }
    }

    return success;
  }

  private boolean remapVar(Exprent exprent, VarVersionPair from, VarVersionPair to) {
    if (exprent == null) { // Sometimes there are null exprents?
      return false;
    }
    List<Exprent> lst = exprent.getAllExprents(true);
    lst.add(exprent);

    boolean remapped = false;

    for (Exprent expr : lst) {
      if (expr.type == Exprent.EXPRENT_ASSIGNMENT) {
        AssignmentExprent ass = (AssignmentExprent)expr;
        if (ass.getLeft().type == Exprent.EXPRENT_VAR && ass.getRight().type == Exprent.EXPRENT_CONST) {
          VarVersionPair left = new VarVersionPair((VarExprent)ass.getLeft());
          if (!left.equals(from) && !left.equals(to)) {
            continue;
          }

          ConstExprent right = (ConstExprent)ass.getRight();
          if (right.getConstType() == VarType.VARTYPE_NULL) {
            continue;
          }
          VarType merged = getMergedType(from, to);
          if (merged == null) { // Types incompatible, do not merge
            continue;
          }

          right.setConstType(merged);
        }
      }
      else if (expr.type == Exprent.EXPRENT_VAR) {
        VarExprent var = (VarExprent)expr;
        VarVersionPair old = new VarVersionPair(var);
        if (!old.equals(from)) {
          continue;
        }
        VarType merged = getMergedType(from, to);
        if (merged == null) { // Types incompatible, do not merge
          continue;
        }

        var.setIndex(to.var);
        var.setVersion(to.version);
        var.setVarType(merged);
        if (var.isDefinition()) {
          var.setDefinition(false);
        }
        varproc.setVarType(to, merged);
        remapped = true;
      }
    }
    return remapped;
  }

  private VarType getMergedType(VarVersionPair from, VarVersionPair to) {
    Map<VarVersionPair, VarType> minTypes = varproc.getVarVersions().getTypeProcessor().getMinExprentTypes();
    Map<VarVersionPair, VarType> maxTypes = varproc.getVarVersions().getTypeProcessor().getMaxExprentTypes();
    return getMergedType(minTypes.get(from), minTypes.get(to), maxTypes.get(from), maxTypes.get(to));
  }

  private static VarType getMergedType(VarType fromMin, VarType toMin, VarType fromMax, VarType toMax) {
    if (fromMin != null && fromMin.equals(toMin)) {
      return fromMin; // Short circuit this for simplicities sake
    }
    VarType type = fromMin == null ? toMin : (toMin == null ? fromMin : VarType.getCommonSupertype(fromMin, toMin));
    if (type == null || fromMin == null || toMin == null) {
      return null; // no common supertype, skip the remapping
    }
    if (type.getType() == CodeConstants.TYPE_OBJECT) {
      if (toMax != null) { // The target var is used in direct invocations
        if (fromMax != null) {
          // Max types are the highest class that this variable is used as a direct instance of without any casts.
          // This will pull up the to var type if the from requires a higher class type.
          // EXA: Collection -> List
          if (DecompilerContext.getStructContext().instanceOf(fromMax.getValue(), toMax.getValue()))
            return fromMax;
        } else {//fromMin != null
          // Pull to up to from: List -> ArrayList
          if (DecompilerContext.getStructContext().instanceOf(fromMin.getValue(), toMax.getValue()))
            return fromMin;
        }
      } else {//toMin != null
        if (fromMax != null) {
          if (DecompilerContext.getStructContext().instanceOf(fromMax.getValue(), toMin.getValue()))
            return fromMax;
        } else {//fromMin != null
          if (DecompilerContext.getStructContext().instanceOf(toMin.getValue(), fromMin.getValue()))
            return toMin;
        }
      }
      return null;
    } else {
      return type;
    }
  }

  private void propogateLVTs(Statement stat) {
    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
    Map<VarVersionPair, VarInfo> types = new LinkedHashMap<>();

    if (varproc.hasLVT()) {
      int index = 0;
      if (!mt.hasModifier(CodeConstants.ACC_STATIC)) {
        List<LocalVariable> lvt = varproc.getCandidates(index); // Some enums give incomplete lvts?
        if (lvt != null && !lvt.isEmpty()) {
          types.put(new VarVersionPair(index, 0), new VarInfo(lvt.get(0), null));
        }
        index++;
      }

      for (VarType var : md.params) {
        List<LocalVariable> lvt = varproc.getCandidates(index); // Some enums give incomplete lvts?
        if (lvt != null && !lvt.isEmpty()) {
          types.put(new VarVersionPair(index, 0), new VarInfo(lvt.get(0), null));
        }
        index += var.getStackSize();
      }
    }

    findTypes(stat, types);

    Map<VarVersionPair,String> typeNames = new LinkedHashMap<>();
    for (Entry<VarVersionPair, VarInfo> e : types.entrySet()) {
      typeNames.put(e.getKey(), e.getValue().getCast());
    }


    Map<VarVersionPair, String> renames = this.mt.getVariableNamer().rename(typeNames);

    // Stuff the parent context into enclosed child methods
    StatementIterator.iterate(root, (exprent) -> {
      List<StructMethod> methods = new ArrayList<>();
      if (exprent.type == Exprent.EXPRENT_VAR) {
        VarExprent var = (VarExprent)exprent;
        if (var.isClassDef()) {
          ClassesProcessor.ClassNode child = DecompilerContext.getClassProcessor().getMapRootClasses().get(var.getVarType().getValue());
          if (child != null)
            methods.addAll(child.classStruct.getMethods());
        }
      }
      else if (exprent.type == Exprent.EXPRENT_NEW) {
        NewExprent _new = (NewExprent)exprent;
        if (_new.isAnonymous()) { //TODO: Check for Lambda here?
          ClassesProcessor.ClassNode child = DecompilerContext.getClassProcessor().getMapRootClasses().get(_new.getNewType().getValue());
          if (child != null) {
            if (_new.isLambda()) {
              if (child.lambdaInformation.is_method_reference) {
                //methods.add(child.getWrapper().getClassStruct().getMethod(child.lambdaInformation.content_method_key));
              } else {
                methods.add(child.classStruct.getMethod(child.lambdaInformation.content_method_name, child.lambdaInformation.content_method_descriptor));
              }
            } else {
              methods.addAll(child.classStruct.getMethods());
            }
          }
        }
      }

      for (StructMethod meth : methods) {
        meth.getVariableNamer().addParentContext(VarDefinitionHelper.this.mt.getVariableNamer());
      }
      return 0;
    });

    if (mt.enclosedClasses != null) {
      for (String cls : mt.enclosedClasses) {
        StructClass cl = DecompilerContext.getStructContext().getClass(cls);
        for (StructMethod meth : cl.getMethods()) {
          meth.getVariableNamer().addParentContext(this.mt.getVariableNamer());
        }
      }
    }

    Map<VarVersionPair, LocalVariable> lvts = new HashMap<>();

    for (Entry<VarVersionPair, VarInfo> e : types.entrySet()) {
      VarVersionPair idx = e.getKey();
      // skip this. we can't rename it
      if (idx.var == 0 && !mt.hasModifier(CodeConstants.ACC_STATIC)) {
        continue;
      }
      LocalVariable lvt = e.getValue().getLVT();
      String rename = renames == null ? null : renames.get(idx);

      if (rename != null) {
        varproc.setVarName(idx, rename);
      }

      if (lvt != null) {
        if (rename != null) {
          lvt = lvt.rename(rename);
        }
        varproc.setVarLVT(idx, lvt);
        lvts.put(idx, lvt);
      }
    }


    applyTypes(stat, lvts);
  }

  private static void findTypes(Statement stat, Map<VarVersionPair, VarInfo> types) {
    if (stat == null) {
      return;
    }

    for (Exprent exp : stat.getVarDefinitions()) {
      findTypes(exp, types);
    }

    if (stat.getExprents() == null) {
      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement) {
          findTypes((Statement)obj, types);
        }
        else if (obj instanceof Exprent) {
          findTypes((Exprent)obj, types);
        }
      }
    }
    else {
      for (Exprent exp : stat.getExprents()) {
        findTypes(exp, types);
      }
    }
  }

  private static void findTypes(Exprent exp, Map<VarVersionPair, VarInfo> types) {
    List<Exprent> lst = exp.getAllExprents(true);
    lst.add(exp);

    for (Exprent exprent : lst) {
      if (exprent.type == Exprent.EXPRENT_VAR) {
        VarExprent var = (VarExprent)exprent;
        VarVersionPair ver = new VarVersionPair(var);
        if (var.isDefinition()) {
          types.put(ver, new VarInfo(var.getLVT(), var.getVarType()));
        } else {
          VarInfo existing = types.get(ver);
          if (existing == null)
            existing = new VarInfo(var.getLVT(), var.getVarType());
          else if (existing.getLVT() == null && var.getLVT() != null)
            existing = new VarInfo(var.getLVT(), existing.getType());
          types.put(ver, existing);
        }
      }
    }
  }

  private static void applyTypes(Statement stat, Map<VarVersionPair, LocalVariable> types) {
    if (stat == null || types.isEmpty()) {
      return;
    }

    for (Exprent exp : stat.getVarDefinitions()) {
      applyTypes(exp, types);
    }

    if (stat.getExprents() == null) {
      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement) {
          applyTypes((Statement)obj, types);
        }
        else if (obj instanceof Exprent) {
          applyTypes((Exprent)obj, types);
        }
      }
    }
    else {
      for (Exprent exp : stat.getExprents()) {
        applyTypes(exp, types);
      }
    }
  }

  private static void applyTypes(Exprent exprent, Map<VarVersionPair, LocalVariable> types) {
    if (exprent == null) {
      return;
    }
    List<Exprent> lst = exprent.getAllExprents(true);
    lst.add(exprent);

    for (Exprent expr : lst) {
      if (expr.type == Exprent.EXPRENT_VAR) {
        VarExprent var = (VarExprent)expr;
        LocalVariable lvt = types.get(new VarVersionPair(var));
        if (lvt != null) {
          var.setLVT(lvt);
        }
      }
    }
  }

  //Helper classes because Java is dumb and doesn't have a Pair<K,V> class
  private static class SimpleEntry<K, V> implements Entry<K, V> {
    private final K key;
    private V value;
    private SimpleEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }
    @Override public K getKey() { return key; }
    @Override public V getValue() { return value; }
    @Override
    public V setValue(V value) {
      V tmp = this.value;
      this.value = value;
      return tmp;
    }
  }
  private static class VPPEntry extends SimpleEntry<VarVersionPair, VarVersionPair> {
    private VPPEntry(VarExprent key, VarVersionPair value) {
        super(new VarVersionPair(key), value);
    }
  }

  private static class VarInfo {
    private final LocalVariable lvt;
    private final String cast;
    private final VarType type;

    private VarInfo(LocalVariable lvt, VarType type) {
      if (lvt != null && lvt.getSignature() != null)
        this.cast = ExprProcessor.getCastTypeName(GenericType.parse(lvt.getSignature()), false, Collections.emptyList());
      else if (lvt != null)
        this.cast = ExprProcessor.getCastTypeName(lvt.getVarType(), false, Collections.emptyList());
      else if (type != null)
        this.cast = ExprProcessor.getCastTypeName(type, false, Collections.emptyList());
      else
        this.cast = "this";
      this.lvt = lvt;
      this.type = type;
    }

    public LocalVariable getLVT() {
      return this.lvt;
    }

    public String getCast() {
      return this.cast;
    }

    public VarType getType() {
      return this.type;
    }
  }

  private static boolean isVarReadFirst(VarVersionPair var, Statement stat, int index, VarExprent... whitelist) {
    if (stat.getExprents() == null) {
      List<Object> objs = stat.getSequentialObjects();
      for (int x = index; x < objs.size(); x++) {
        Object obj = objs.get(x);
        if (obj instanceof Statement) {
          if (isVarReadFirst(var, (Statement)obj, 0, whitelist)) {
            return true;
          }
        }
        else if (obj instanceof Exprent) {
          if (isVarReadFirst(var, (Exprent)obj, whitelist)) {
            return true;
          }
        }
      }
    }
    else {
      for (int x = index; x < stat.getExprents().size(); x++) {
        if (isVarReadFirst(var, stat.getExprents().get(x), whitelist)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isVarReadFirst(VarVersionPair target, Exprent exp, VarExprent... whitelist) {
    AssignmentExprent ass = exp.type == Exprent.EXPRENT_ASSIGNMENT ? (AssignmentExprent)exp : null;
    List<Exprent> lst = exp.getAllExprents(true);
    lst.add(exp);
    for (Exprent ex : lst) {
      if (ex.type == Exprent.EXPRENT_VAR) {
        VarExprent var = (VarExprent)ex;
        if (var.getIndex() == target.var && var.getVersion() == target.version) {
          boolean allowed = false;
          if (ass != null) {
            if (var == ass.getLeft()) {
              allowed = true;
            }
          }
          for (VarExprent white : whitelist) {
            if (var == white) {
              allowed = true;
              break;
            }
          }
          if (!allowed) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
