/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main.rels;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

public class NestedMemberAccess {

  private static final int METHOD_ACCESS_NORMAL = 1;
  private static final int METHOD_ACCESS_FIELD_GET = 2;
  private static final int METHOD_ACCESS_FIELD_SET = 3;
  private static final int METHOD_ACCESS_METHOD = 4;

  private boolean noSynthFlag;
  private final Map<MethodWrapper, Integer> mapMethodType = new HashMap<MethodWrapper, Integer>();


  public void propagateMemberAccess(ClassNode root) {
    if (root.nested.isEmpty()) {
      return;
    }

    noSynthFlag = DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET);

    computeMethodTypes(root);

    eliminateStaticAccess(root);
  }


  private void computeMethodTypes(ClassNode node) {
    if (node.type == ClassNode.CLASS_LAMBDA) {
      return;
    }

    for (ClassNode nd : node.nested) {
      computeMethodTypes(nd);
    }

    for (MethodWrapper method : node.getWrapper().getMethods()) {
      computeMethodType(node, method);
    }
  }

  private void computeMethodType(ClassNode node, MethodWrapper method) {
    int type = METHOD_ACCESS_NORMAL;

    if (method.root != null) {
      DirectGraph graph = method.getOrBuildGraph();

      StructMethod mt = method.methodStruct;
      if ((noSynthFlag || mt.isSynthetic()) && mt.hasModifier(CodeConstants.ACC_STATIC)) {
        if (graph.nodes.size() == 2) {  // incl. dummy exit node
          if (graph.first.exprents.size() == 1) {
            Exprent exprent = graph.first.exprents.get(0);

            MethodDescriptor mtdesc = MethodDescriptor.parseDescriptor(mt.getDescriptor());
            int parcount = mtdesc.params.length;

            Exprent exprCore = exprent;

            if (exprent.type == Exprent.EXPRENT_EXIT) {
              ExitExprent exexpr = (ExitExprent)exprent;
              if (exexpr.getExitType() == ExitExprent.EXIT_RETURN && exexpr.getValue() != null) {
                exprCore = exexpr.getValue();
              }
            }

            switch (exprCore.type) {
              case Exprent.EXPRENT_FIELD:
                FieldExprent fexpr = (FieldExprent)exprCore;
                if ((parcount == 1 && !fexpr.isStatic()) ||
                    (parcount == 0 && fexpr.isStatic())) {
                  if (fexpr.getClassname().equals(node.classStruct.qualifiedName)) {  // FIXME: check for private flag of the field
                    if (fexpr.isStatic() ||
                        (fexpr.getInstance().type == Exprent.EXPRENT_VAR && ((VarExprent)fexpr.getInstance()).getIndex() == 0)) {
                      type = METHOD_ACCESS_FIELD_GET;
                    }
                  }
                }
                break;
              case Exprent.EXPRENT_VAR:  // qualified this
                if (parcount == 1) {
                  // this or final variable
                  if (((VarExprent)exprCore).getIndex() != 0) {
                    type = METHOD_ACCESS_FIELD_GET;
                  }
                }

                break;
              case Exprent.EXPRENT_INVOCATION:
                type = METHOD_ACCESS_METHOD;
                break;
              case Exprent.EXPRENT_ASSIGNMENT:
                AssignmentExprent asexpr = (AssignmentExprent)exprCore;
                if (asexpr.getLeft().type == Exprent.EXPRENT_FIELD && asexpr.getRight().type == Exprent.EXPRENT_VAR) {
                  FieldExprent fexpras = (FieldExprent)asexpr.getLeft();
                  if ((parcount == 2 && !fexpras.isStatic()) ||
                      (parcount == 1 && fexpras.isStatic())) {
                    if (fexpras.getClassname().equals(node.classStruct.qualifiedName)) { // FIXME: check for private flag of the field
                      if (fexpras.isStatic() ||
                          (fexpras.getInstance().type == Exprent.EXPRENT_VAR && ((VarExprent)fexpras.getInstance()).getIndex() == 0)) {
                        if (((VarExprent)asexpr.getRight()).getIndex() == parcount - 1) {
                          type = METHOD_ACCESS_FIELD_SET;
                        }
                      }
                    }
                  }
                }
            }


            if (type == METHOD_ACCESS_METHOD) { // FIXME: check for private flag of the method

              type = METHOD_ACCESS_NORMAL;

              InvocationExprent invexpr = (InvocationExprent)exprCore;

              if ((invexpr.isStatic() && invexpr.getLstParameters().size() == parcount) ||
                  (!invexpr.isStatic() && invexpr.getInstance().type == Exprent.EXPRENT_VAR
                   && ((VarExprent)invexpr.getInstance()).getIndex() == 0 && invexpr.getLstParameters().size() == parcount - 1)) {

                boolean equalpars = true;

                for (int i = 0; i < invexpr.getLstParameters().size(); i++) {
                  Exprent parexpr = invexpr.getLstParameters().get(i);
                  if (parexpr.type != Exprent.EXPRENT_VAR ||
                      ((VarExprent)parexpr).getIndex() != i + (invexpr.isStatic() ? 0 : 1)) {
                    equalpars = false;
                    break;
                  }
                }

                if (equalpars) {
                  type = METHOD_ACCESS_METHOD;
                }
              }
            }
          }
          else if (graph.first.exprents.size() == 2) {
            Exprent exprentFirst = graph.first.exprents.get(0);
            Exprent exprentSecond = graph.first.exprents.get(1);

            if (exprentFirst.type == Exprent.EXPRENT_ASSIGNMENT &&
                exprentSecond.type == Exprent.EXPRENT_EXIT) {

              MethodDescriptor mtdesc = MethodDescriptor.parseDescriptor(mt.getDescriptor());
              int parcount = mtdesc.params.length;

              AssignmentExprent asexpr = (AssignmentExprent)exprentFirst;
              if (asexpr.getLeft().type == Exprent.EXPRENT_FIELD && asexpr.getRight().type == Exprent.EXPRENT_VAR) {
                FieldExprent fexpras = (FieldExprent)asexpr.getLeft();
                if ((parcount == 2 && !fexpras.isStatic()) ||
                    (parcount == 1 && fexpras.isStatic())) {
                  if (fexpras.getClassname().equals(node.classStruct.qualifiedName)) { // FIXME: check for private flag of the field
                    if (fexpras.isStatic() ||
                        (fexpras.getInstance().type == Exprent.EXPRENT_VAR && ((VarExprent)fexpras.getInstance()).getIndex() == 0)) {
                      if (((VarExprent)asexpr.getRight()).getIndex() == parcount - 1) {

                        ExitExprent exexpr = (ExitExprent)exprentSecond;
                        if (exexpr.getExitType() == ExitExprent.EXIT_RETURN && exexpr.getValue() != null) {
                          if (exexpr.getValue().type == Exprent.EXPRENT_VAR &&
                              ((VarExprent)asexpr.getRight()).getIndex() == parcount - 1) {
                            type = METHOD_ACCESS_FIELD_SET;
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    if (type != METHOD_ACCESS_NORMAL) {
      mapMethodType.put(method, type);
    }
    else {
      mapMethodType.remove(method);
    }
  }


  private void eliminateStaticAccess(ClassNode node) {

    if (node.type == ClassNode.CLASS_LAMBDA) {
      return;
    }

    for (MethodWrapper meth : node.getWrapper().getMethods()) {

      if (meth.root != null) {

        boolean replaced = false;

        DirectGraph graph = meth.getOrBuildGraph();

        HashSet<DirectNode> setVisited = new HashSet<DirectNode>();
        LinkedList<DirectNode> stack = new LinkedList<DirectNode>();
        stack.add(graph.first);

        while (!stack.isEmpty()) {  // TODO: replace with interface iterator?

          DirectNode nd = stack.removeFirst();

          if (setVisited.contains(nd)) {
            continue;
          }
          setVisited.add(nd);

          for (int i = 0; i < nd.exprents.size(); i++) {
            Exprent exprent = nd.exprents.get(i);

            replaced |= replaceInvocations(node, meth, exprent);

            if (exprent.type == Exprent.EXPRENT_INVOCATION) {
              Exprent ret = replaceAccessExprent(node, meth, (InvocationExprent)exprent);

              if (ret != null) {
                nd.exprents.set(i, ret);
                replaced = true;
              }
            }
          }

          for (DirectNode ndx : nd.succs) {
            stack.add(ndx);
          }
        }

        if (replaced) {
          computeMethodType(node, meth);
        }
      }
    }

    for (ClassNode child : node.nested) {
      eliminateStaticAccess(child);
    }
  }


  private boolean replaceInvocations(ClassNode caller, MethodWrapper meth, Exprent exprent) {

    boolean res = false;

    for (Exprent expr : exprent.getAllExprents()) {
      res |= replaceInvocations(caller, meth, expr);
    }

    while (true) {

      boolean found = false;

      for (Exprent expr : exprent.getAllExprents()) {
        if (expr.type == Exprent.EXPRENT_INVOCATION) {
          Exprent newexpr = replaceAccessExprent(caller, meth, (InvocationExprent)expr);
          if (newexpr != null) {
            exprent.replaceExprent(expr, newexpr);
            found = true;
            res = true;
            break;
          }
        }
      }

      if (!found) {
        break;
      }
    }

    return res;
  }

  private static boolean sameTree(ClassNode caller, ClassNode callee) {

    if (caller.classStruct.qualifiedName.equals(callee.classStruct.qualifiedName)) {
      return false;
    }

    while (caller.parent != null) {
      caller = caller.parent;
    }

    while (callee.parent != null) {
      callee = callee.parent;
    }

    return caller == callee;
  }

  private Exprent replaceAccessExprent(ClassNode caller, MethodWrapper methdest, InvocationExprent invexpr) {

    ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(invexpr.getClassname());

    MethodWrapper methsource = null;
    if (node != null && node.getWrapper() != null) {
      methsource = node.getWrapper().getMethodWrapper(invexpr.getName(), invexpr.getStringDescriptor());
    }

    if (methsource == null || !mapMethodType.containsKey(methsource)) {
      return null;
    }

    // if same method, return
    if (node.classStruct.qualifiedName.equals(caller.classStruct.qualifiedName) &&
        methsource.methodStruct.getName().equals(methdest.methodStruct.getName()) &&
        methsource.methodStruct.getDescriptor().equals(methdest.methodStruct.getDescriptor())) {
      // no recursive invocations permitted!
      return null;
    }

    int type = mapMethodType.get(methsource);

    //		// FIXME: impossible case. METHOD_ACCESS_NORMAL is not saved in the map
    //		if(type == METHOD_ACCESS_NORMAL) {
    //			return null;
    //		}

    if (!sameTree(caller, node)) {
      return null;
    }

    DirectGraph graph = methsource.getOrBuildGraph();
    Exprent source = graph.first.exprents.get(0);

    Exprent retexprent = null;

    switch (type) {
      case METHOD_ACCESS_FIELD_GET:
        ExitExprent exsource = (ExitExprent)source;
        if (exsource.getValue().type == Exprent.EXPRENT_VAR) { // qualified this
          VarExprent var = (VarExprent)exsource.getValue();
          String varname = methsource.varproc.getVarName(new VarVersionPair(var));

          if (!methdest.setOuterVarNames.contains(varname)) {
            VarNamesCollector vnc = new VarNamesCollector();
            vnc.addName(varname);

            methdest.varproc.refreshVarNames(vnc);
            methdest.setOuterVarNames.add(varname);
          }

          int index = methdest.counter.getCounterAndIncrement(CounterContainer.VAR_COUNTER);
          VarExprent ret = new VarExprent(index, var.getVarType(), methdest.varproc);
          methdest.varproc.setVarName(new VarVersionPair(index, 0), varname);

          retexprent = ret;
        }
        else { // field
          FieldExprent ret = (FieldExprent)exsource.getValue().copy();
          if (!ret.isStatic()) {
            ret.replaceExprent(ret.getInstance(), invexpr.getLstParameters().get(0));
          }
          retexprent = ret;
        }
        break;
      case METHOD_ACCESS_FIELD_SET:
        AssignmentExprent ret;
        if (source.type == Exprent.EXPRENT_EXIT) {
          ExitExprent extex = (ExitExprent)source;
          ret = (AssignmentExprent)extex.getValue().copy();
        }
        else {
          ret = (AssignmentExprent)source.copy();
        }
        FieldExprent fexpr = (FieldExprent)ret.getLeft();

        if (fexpr.isStatic()) {
          ret.replaceExprent(ret.getRight(), invexpr.getLstParameters().get(0));
        }
        else {
          ret.replaceExprent(ret.getRight(), invexpr.getLstParameters().get(1));
          fexpr.replaceExprent(fexpr.getInstance(), invexpr.getLstParameters().get(0));
        }
        retexprent = ret;
        break;
      case METHOD_ACCESS_METHOD:
        if (source.type == Exprent.EXPRENT_EXIT) {
          source = ((ExitExprent)source).getValue();
        }

        InvocationExprent invret = (InvocationExprent)source.copy();

        int index = 0;
        if (!invret.isStatic()) {
          invret.replaceExprent(invret.getInstance(), invexpr.getLstParameters().get(0));
          index = 1;
        }

        for (int i = 0; i < invret.getLstParameters().size(); i++) {
          invret.replaceExprent(invret.getLstParameters().get(i), invexpr.getLstParameters().get(i + index));
        }

        retexprent = invret;
    }


    if (retexprent != null) {
      // hide synthetic access method
      boolean hide = true;

      if (node.type == ClassNode.CLASS_ROOT || (node.access & CodeConstants.ACC_STATIC) != 0) {
        StructMethod mt = methsource.methodStruct;
        if (!mt.isSynthetic()) {
          hide = false;
        }
      }
      if (hide) {
        node.getWrapper().getHiddenMembers().add(InterpreterUtil.makeUniqueKey(invexpr.getName(), invexpr.getStringDescriptor()));
      }
    }

    return retexprent;
  }
}
