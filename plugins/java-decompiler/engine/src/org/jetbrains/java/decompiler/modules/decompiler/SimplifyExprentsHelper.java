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
package org.jetbrains.java.decompiler.modules.decompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ArrayExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FieldExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.MonitorExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAConstructorSparseEx;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

public class SimplifyExprentsHelper {

  static final MatchEngine class14Builder = new MatchEngine();
  
  private final boolean firstInvocation;

  public SimplifyExprentsHelper(boolean firstInvocation) {
    this.firstInvocation = firstInvocation;
  }

  public boolean simplifyStackVarsStatement(Statement stat, HashSet<Integer> setReorderedIfs, SSAConstructorSparseEx ssa, StructClass cl) {

    boolean res = false;

    if (stat.getExprents() == null) {

      boolean processClass14 = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_CLASS_1_4);

      while (true) {

        boolean changed = false;

        for (Statement st : stat.getStats()) {
          res |= simplifyStackVarsStatement(st, setReorderedIfs, ssa, cl);

          // collapse composed if's
          if (changed = IfHelper.mergeIfs(st, setReorderedIfs)) {
            break;
          }

          // collapse iff ?: statement
          if (changed = buildIff(st, ssa)) {
            break;
          }

          // collapse inlined .class property in version 1.4 and before
          if (processClass14 && (changed = collapseInlinedClass14(st))) {
            break;
          }
        }

        res |= changed;

        if (!changed) {
          break;
        }
      }
    }
    else {
      res |= simplifyStackVarsExprents(stat.getExprents(), cl);
    }

    return res;
  }

  private boolean simplifyStackVarsExprents(List<Exprent> list, StructClass cl) {

    boolean res = false;

    int index = 0;

    while (index < list.size()) {

      Exprent current = list.get(index);

      Exprent ret = isSimpleConstructorInvocation(current);
      if (ret != null) {
        list.set(index, ret);
        res = true;

        continue;
      }

      // lambda expression (Java 8)
      ret = isLambda(current, cl);
      if (ret != null) {
        list.set(index, ret);
        res = true;

        continue;
      }

      // remove monitor exit
      if (isMonitorExit(current)) {
        list.remove(index);
        res = true;

        continue;
      }

      // trivial assignment of a stack variable
      if (isTrivialStackAssignment(current)) {
        list.remove(index);
        res = true;

        continue;
      }

      if (index == list.size() - 1) {
        break;
      }


      Exprent next = list.get(index + 1);


      // constructor invocation
      if (isConstructorInvocationRemote(list, index)) {
        list.remove(index);
        res = true;

        continue;
      }

      // remove getClass() invocation, which is part of a qualified new
      if (DecompilerContext.getOption(IFernflowerPreferences.REMOVE_GET_CLASS_NEW)) {
        if (isQualifiedNewGetClass(current, next)) {
          list.remove(index);
          res = true;

          continue;
        }
      }

      // direct initialization of an array
      int arrcount = isArrayInitializer(list, index);
      if (arrcount > 0) {
        for (int i = 0; i < arrcount; i++) {
          list.remove(index + 1);
        }
        res = true;

        continue;
      }

      // add array initializer expression
      if (addArrayInitializer(current, next)) {
        list.remove(index + 1);
        res = true;

        continue;
      }

      // integer ++expr and --expr  (except for vars!)
      Exprent func = isPPIorMMI(current);
      if (func != null) {
        list.set(index, func);
        res = true;

        continue;
      }

      // expr++ and expr--
      if (isIPPorIMM(current, next)) {
        list.remove(index + 1);
        res = true;

        continue;
      }

      // assignment on stack
      if (isStackAssignement(current, next)) {
        list.remove(index + 1);
        res = true;

        continue;
      }

      if (!firstInvocation && isStackAssignement2(current, next)) {
        list.remove(index + 1);
        res = true;

        continue;
      }

      index++;
    }

    return res;
  }

  private static boolean addArrayInitializer(Exprent first, Exprent second) {

    if (first.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)first;

      if (as.getRight().type == Exprent.EXPRENT_NEW && as.getLeft().type == Exprent.EXPRENT_VAR) {
        NewExprent newex = (NewExprent)as.getRight();

        if (!newex.getLstArrayElements().isEmpty()) {

          VarExprent arrvar = (VarExprent)as.getLeft();

          if (second.type == Exprent.EXPRENT_ASSIGNMENT) {
            AssignmentExprent aas = (AssignmentExprent)second;
            if (aas.getLeft().type == Exprent.EXPRENT_ARRAY) {
              ArrayExprent arrex = (ArrayExprent)aas.getLeft();
              if (arrex.getArray().type == Exprent.EXPRENT_VAR && arrvar.equals(arrex.getArray())
                  && arrex.getIndex().type == Exprent.EXPRENT_CONST) {

                int constvalue = ((ConstExprent)arrex.getIndex()).getIntValue();

                if (constvalue < newex.getLstArrayElements().size()) {
                  Exprent init = newex.getLstArrayElements().get(constvalue);
                  if (init.type == Exprent.EXPRENT_CONST) {
                    ConstExprent cinit = (ConstExprent)init;

                    VarType arrtype = newex.getNewType().decreaseArrayDim();

                    ConstExprent defaultval = ExprProcessor.getDefaultArrayValue(arrtype);

                    if (cinit.equals(defaultval)) {

                      Exprent tempexpr = aas.getRight();

                      if (!tempexpr.containsExprent(arrvar)) {
                        newex.getLstArrayElements().set(constvalue, tempexpr);

                        if (tempexpr.type == Exprent.EXPRENT_NEW) {
                          NewExprent tempnewex = (NewExprent)tempexpr;
                          int dims = newex.getNewType().arrayDim;
                          if (dims > 1 && !tempnewex.getLstArrayElements().isEmpty()) {
                            tempnewex.setDirectArrayInit(true);
                          }
                        }

                        return true;
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

    return false;
  }


  private static int isArrayInitializer(List<Exprent> list, int index) {

    Exprent current = list.get(index);
    if (current.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)current;

      if (as.getRight().type == Exprent.EXPRENT_NEW && as.getLeft().type == Exprent.EXPRENT_VAR) {
        NewExprent newex = (NewExprent)as.getRight();

        if (newex.getExprType().arrayDim > 0 && newex.getLstDims().size() == 1 && newex.getLstArrayElements().isEmpty() &&
            newex.getLstDims().get(0).type == Exprent.EXPRENT_CONST) {

          int size = ((Integer)((ConstExprent)newex.getLstDims().get(0)).getValue()).intValue();
          if (size == 0) {
            return 0;
          }

          VarExprent arrvar = (VarExprent)as.getLeft();

          HashMap<Integer, Exprent> mapInit = new HashMap<>();

          int i = 1;
          while (index + i < list.size() && i <= size) {
            boolean found = false;

            Exprent expr = list.get(index + i);
            if (expr.type == Exprent.EXPRENT_ASSIGNMENT) {
              AssignmentExprent aas = (AssignmentExprent)expr;
              if (aas.getLeft().type == Exprent.EXPRENT_ARRAY) {
                ArrayExprent arrex = (ArrayExprent)aas.getLeft();
                if (arrex.getArray().type == Exprent.EXPRENT_VAR && arrvar.equals(arrex.getArray())
                    && arrex.getIndex().type == Exprent.EXPRENT_CONST) {

                  int constvalue = ((ConstExprent)arrex.getIndex())
                    .getIntValue(); // TODO: check for a number type. Failure extremely improbable, but nevertheless...

                  if (constvalue < size && !mapInit.containsKey(constvalue)) {

                    if (!aas.getRight().containsExprent(arrvar)) {
                      mapInit.put(constvalue, aas.getRight());
                      found = true;
                    }
                  }
                }
              }
            }

            if (!found) {
              break;
            }

            i++;
          }

          double fraction = ((double)mapInit.size()) / size;

          if ((arrvar.isStack() && fraction > 0) || (size <= 7 && fraction >= 0.3) ||
              (size > 7 && fraction >= 0.7)) {

            List<Exprent> lstRet = new ArrayList<>();

            VarType arrtype = newex.getNewType().decreaseArrayDim();

            ConstExprent defaultval = ExprProcessor.getDefaultArrayValue(arrtype);

            for (int j = 0; j < size; j++) {
              lstRet.add(defaultval.copy());
            }

            int dims = newex.getNewType().arrayDim;
            for (Entry<Integer, Exprent> ent : mapInit.entrySet()) {
              Exprent tempexpr = ent.getValue();
              lstRet.set(ent.getKey(), tempexpr);

              if (tempexpr.type == Exprent.EXPRENT_NEW) {
                NewExprent tempnewex = (NewExprent)tempexpr;
                if (dims > 1 && !tempnewex.getLstArrayElements().isEmpty()) {
                  tempnewex.setDirectArrayInit(true);
                }
              }
            }

            newex.setLstArrayElements(lstRet);

            return mapInit.size();
          }
        }
      }
    }

    return 0;
  }

  private static boolean isTrivialStackAssignment(Exprent first) {

    if (first.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent asf = (AssignmentExprent)first;

      if (asf.getLeft().type == Exprent.EXPRENT_VAR && asf.getRight().type == Exprent.EXPRENT_VAR) {
        VarExprent varleft = (VarExprent)asf.getLeft();
        VarExprent varright = (VarExprent)asf.getRight();

        if (varleft.getIndex() == varright.getIndex() && varleft.isStack() &&
            varright.isStack()) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isStackAssignement2(Exprent first, Exprent second) {  // e.g. 1.4-style class invocation

    if (first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent asf = (AssignmentExprent)first;
      AssignmentExprent ass = (AssignmentExprent)second;

      if (asf.getLeft().type == Exprent.EXPRENT_VAR && ass.getRight().type == Exprent.EXPRENT_VAR &&
          asf.getLeft().equals(ass.getRight()) && ((VarExprent)asf.getLeft()).isStack()) {
        if (ass.getLeft().type != Exprent.EXPRENT_VAR || !((VarExprent)ass.getLeft()).isStack()) {
          asf.setRight(new AssignmentExprent(ass.getLeft(), asf.getRight(), ass.bytecode));
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isStackAssignement(Exprent first, Exprent second) {

    if (first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent asf = (AssignmentExprent)first;
      AssignmentExprent ass = (AssignmentExprent)second;

      while (true) {
        if (asf.getRight().equals(ass.getRight())) {
          if ((asf.getLeft().type == Exprent.EXPRENT_VAR && ((VarExprent)asf.getLeft()).isStack()) &&
              (ass.getLeft().type != Exprent.EXPRENT_VAR || !((VarExprent)ass.getLeft()).isStack())) {

            if (!ass.getLeft().containsExprent(asf.getLeft())) {
              asf.setRight(ass);
              return true;
            }
          }
        }
        if (asf.getRight().type == Exprent.EXPRENT_ASSIGNMENT) {
          asf = (AssignmentExprent)asf.getRight();
        }
        else {
          break;
        }
      }
    }

    return false;
  }

  private static Exprent isPPIorMMI(Exprent first) {

    if (first.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)first;

      if (as.getRight().type == Exprent.EXPRENT_FUNCTION) {
        FunctionExprent func = (FunctionExprent)as.getRight();

        if (func.getFuncType() == FunctionExprent.FUNCTION_ADD ||
            func.getFuncType() == FunctionExprent.FUNCTION_SUB) {
          Exprent econd = func.getLstOperands().get(0);
          Exprent econst = func.getLstOperands().get(1);

          if (econst.type != Exprent.EXPRENT_CONST && econd.type == Exprent.EXPRENT_CONST &&
              func.getFuncType() == FunctionExprent.FUNCTION_ADD) {
            econd = econst;
            econst = func.getLstOperands().get(0);
          }

          if (econst.type == Exprent.EXPRENT_CONST && ((ConstExprent)econst).hasValueOne()) {
            Exprent left = as.getLeft();

            if (left.type != Exprent.EXPRENT_VAR && left.equals(econd)) {
              FunctionExprent ret = new FunctionExprent(
                func.getFuncType() == FunctionExprent.FUNCTION_ADD ? FunctionExprent.FUNCTION_PPI : FunctionExprent.FUNCTION_MMI,
                econd, func.bytecode);
              ret.setImplicitType(VarType.VARTYPE_INT);
              return ret;
            }
          }
        }
      }
    }

    return null;
  }

  private static boolean isIPPorIMM(Exprent first, Exprent second) {

    if (first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_FUNCTION) {
      AssignmentExprent as = (AssignmentExprent)first;
      FunctionExprent in = (FunctionExprent)second;

      if ((in.getFuncType() == FunctionExprent.FUNCTION_MMI || in.getFuncType() == FunctionExprent.FUNCTION_PPI) &&
          in.getLstOperands().get(0).equals(as.getRight())) {

        if (in.getFuncType() == FunctionExprent.FUNCTION_MMI) {
          in.setFuncType(FunctionExprent.FUNCTION_IMM);
        }
        else {
          in.setFuncType(FunctionExprent.FUNCTION_IPP);
        }
        as.setRight(in);

        return true;
      }
    }

    return false;
  }

  private static boolean isMonitorExit(Exprent first) {
    if (first.type == Exprent.EXPRENT_MONITOR) {
      MonitorExprent monexpr = (MonitorExprent)first;
      if (monexpr.getMonType() == MonitorExprent.MONITOR_EXIT && monexpr.getValue().type == Exprent.EXPRENT_VAR
          && !((VarExprent)monexpr.getValue()).isStack()) {
        return true;
      }
    }

    return false;
  }

  private static boolean isQualifiedNewGetClass(Exprent first, Exprent second) {

    if (first.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent invexpr = (InvocationExprent)first;

      if (!invexpr.isStatic() && invexpr.getInstance().type == Exprent.EXPRENT_VAR && invexpr.getName().equals("getClass") &&
          invexpr.getStringDescriptor().equals("()Ljava/lang/Class;")) {

        List<Exprent> lstExprents = second.getAllExprents();
        lstExprents.add(second);

        for (Exprent expr : lstExprents) {
          if (expr.type == Exprent.EXPRENT_NEW) {
            NewExprent nexpr = (NewExprent)expr;
            if (nexpr.getConstructor() != null && !nexpr.getConstructor().getLstParameters().isEmpty() &&
                nexpr.getConstructor().getLstParameters().get(0).equals(invexpr.getInstance())) {

              String classname = nexpr.getNewType().value;
              ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(classname);
              if (node != null && node.type != ClassNode.CLASS_ROOT) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  //	private static boolean isConstructorInvocationRemote(List<Exprent> list, int index) {
  //
  //		Exprent current = list.get(index);
  //
  //		if(current.type == Exprent.EXPRENT_ASSIGNMENT) {
  //			AssignmentExprent as = (AssignmentExprent)current;
  //
  //			if(as.getLeft().type == Exprent.EXPRENT_VAR && as.getRight().type == Exprent.EXPRENT_NEW) {
  //
  //				NewExprent newexpr = (NewExprent)as.getRight();
  //				VarType newtype = newexpr.getNewType();
  //				VarVersionPair leftPaar = new VarVersionPair((VarExprent)as.getLeft());
  //
  //				if(newtype.type == CodeConstants.TYPE_OBJECT && newtype.arrayDim == 0 &&
  //						newexpr.getConstructor() == null) {
  //
  //					Set<VarVersionPair> setChangedVars = new HashSet<VarVersionPair>();
  //
  //					for(int i = index + 1; i < list.size(); i++) {
  //						Exprent remote = list.get(i);
  //
  //						if(remote.type == Exprent.EXPRENT_INVOCATION) {
  //							InvocationExprent in = (InvocationExprent)remote;
  //
  //							if(in.getFuncType() == InvocationExprent.TYP_INIT && in.getInstance().type == Exprent.EXPRENT_VAR
  //									&& as.getLeft().equals(in.getInstance())) {
  //
  //								Set<VarVersionPair>  setVars = remote.getAllVariables();
  //								setVars.remove(leftPaar);
  //								setVars.retainAll(setChangedVars);
  //
  //								if(setVars.isEmpty()) {
  //
  //									newexpr.setConstructor(in);
  //									in.setInstance(null);
  //
  //									if(!setChangedVars.isEmpty()) { // some exprents inbetween
  //										list.add(index+1, as.copy());
  //										list.remove(i+1);
  //									} else {
  //										list.set(i, as.copy());
  //									}
  //
  //									return true;
  //								}
  //							}
  //						}
  //
  //						boolean isTempAssignment = false;
  //
  //						if(remote.type == Exprent.EXPRENT_ASSIGNMENT) {    // ugly solution
  //							AssignmentExprent asremote = (AssignmentExprent)remote;
  //							if(asremote.getLeft().type == Exprent.EXPRENT_VAR &&
  //									asremote.getRight().type == Exprent.EXPRENT_VAR) {
  //								setChangedVars.add(new VarVersionPair((VarExprent)asremote.getLeft()));
  //								isTempAssignment = true;
  //							}
  //
  //							// FIXME: needs to be rewritten
  //							// propagate (var = new X) forward to the <init> invokation and then reduce
  //
  ////							if(asremote.getLeft().type == Exprent.EXPRENT_VAR) {
  ////								List<Exprent> lstRightExprents = asremote.getRight().getAllExprents(true);
  ////								lstRightExprents.add(asremote.getRight());
  ////
  ////								Set<VarVersionPair> setTempChangedVars = new HashSet<VarVersionPair>();
  ////								boolean isTemp = true;
  ////
  ////								for(Exprent expr : lstRightExprents) {
  ////									if(expr.type != Exprent.EXPRENT_VAR && expr.type != Exprent.EXPRENT_FIELD) {
  ////										isTemp = false;
  ////										break;
  ////									} else if(expr.type == Exprent.EXPRENT_VAR) {
  ////										setTempChangedVars.add(new VarVersionPair((VarExprent)expr));
  ////									}
  ////								}
  ////
  ////								if(isTemp) {
  ////									setChangedVars.addAll(setTempChangedVars);
  ////									isTempAssignment = true;
  ////								}
  ////							}
  ////						} else if(remote.type == Exprent.EXPRENT_FUNCTION) {
  ////							FunctionExprent fexpr = (FunctionExprent)remote;
  ////							if(fexpr.getFuncType() == FunctionExprent.FUNCTION_IPP || fexpr.getFuncType() == FunctionExprent.FUNCTION_IMM
  ////									|| fexpr.getFuncType() == FunctionExprent.FUNCTION_PPI || fexpr.getFuncType() == FunctionExprent.FUNCTION_MMI) {
  ////								if(fexpr.getLstOperands().get(0).type == Exprent.EXPRENT_VAR) {
  ////									setChangedVars.add(new VarVersionPair((VarExprent)fexpr.getLstOperands().get(0)));
  ////									isTempAssignment = true;
  ////								}
  ////							}
  //						}
  //
  //						if(!isTempAssignment) {
  //							Set<VarVersionPair> setVars = remote.getAllVariables();
  //							if(setVars.contains(leftPaar)) {
  //								return false;
  //							} else {
  //								setChangedVars.addAll(setVars);
  //							}
  //						}
  //					}
  //				}
  //			}
  //		}
  //
  //		return false;
  //	}

  // propagate (var = new X) forward to the <init> invokation
  private static boolean isConstructorInvocationRemote(List<Exprent> list, int index) {

    Exprent current = list.get(index);

    if (current.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)current;

      if (as.getLeft().type == Exprent.EXPRENT_VAR && as.getRight().type == Exprent.EXPRENT_NEW) {

        NewExprent newexpr = (NewExprent)as.getRight();
        VarType newtype = newexpr.getNewType();
        VarVersionPair leftPaar = new VarVersionPair((VarExprent)as.getLeft());

        if (newtype.type == CodeConstants.TYPE_OBJECT && newtype.arrayDim == 0 && newexpr.getConstructor() == null) {

          for (int i = index + 1; i < list.size(); i++) {
            Exprent remote = list.get(i);

            // <init> invocation
            if (remote.type == Exprent.EXPRENT_INVOCATION) {
              InvocationExprent in = (InvocationExprent)remote;

              if (in.getFunctype() == InvocationExprent.TYP_INIT &&
                  in.getInstance().type == Exprent.EXPRENT_VAR &&
                  as.getLeft().equals(in.getInstance())) {

                newexpr.setConstructor(in);
                in.setInstance(null);

                list.set(i, as.copy());

                return true;
              }
            }

            // check for variable in use
            Set<VarVersionPair> setVars = remote.getAllVariables();
            if (setVars.contains(leftPaar)) { // variable used somewhere in between -> exit, need a better reduced code
              return false;
            }
          }
        }
      }
    }

    return false;
  }

  private static Exprent isLambda(Exprent exprent, StructClass cl) {

    List<Exprent> lst = exprent.getAllExprents();
    for (Exprent expr : lst) {
      Exprent ret = isLambda(expr, cl);
      if (ret != null) {
        exprent.replaceExprent(expr, ret);
      }
    }

    if (exprent.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent in = (InvocationExprent)exprent;

      if (in.getInvocationTyp() == InvocationExprent.INVOKE_DYNAMIC) {

        String lambda_class_name = cl.qualifiedName + in.getInvokeDynamicClassSuffix();
        ClassNode lambda_class = DecompilerContext.getClassProcessor().getMapRootClasses().get(lambda_class_name);

        if (lambda_class != null) { // real lambda class found, replace invocation with an anonymous class

          NewExprent newexp = new NewExprent(new VarType(lambda_class_name, true), null, 0, in.bytecode);
          newexp.setConstructor(in);
          // note: we don't set the instance to null with in.setInstance(null) like it is done for a common constructor invokation
          // lambda can also be a reference to a virtual method (e.g. String x; ...(x::toString);)
          // in this case instance will hold the corresponding object

          return newexp;
        }
      }
    }

    return null;
  }


  private static Exprent isSimpleConstructorInvocation(Exprent exprent) {

    List<Exprent> lst = exprent.getAllExprents();
    for (Exprent expr : lst) {
      Exprent ret = isSimpleConstructorInvocation(expr);
      if (ret != null) {
        exprent.replaceExprent(expr, ret);
      }
    }

    if (exprent.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent in = (InvocationExprent)exprent;
      if (in.getFunctype() == InvocationExprent.TYP_INIT && in.getInstance().type == Exprent.EXPRENT_NEW) {
        NewExprent newexp = (NewExprent)in.getInstance();
        newexp.setConstructor(in);
        in.setInstance(null);
        return newexp;
      }
    }

    return null;
  }


  private static boolean buildIff(Statement stat, SSAConstructorSparseEx ssa) {

    if (stat.type == Statement.TYPE_IF && stat.getExprents() == null) {
      IfStatement stif = (IfStatement)stat;

      Exprent ifheadexpr = stif.getHeadexprent();
      Set<Integer> ifheadexpr_bytecode = (ifheadexpr == null ? null : ifheadexpr.bytecode);

      if (stif.iftype == IfStatement.IFTYPE_IFELSE) {
        Statement ifstat = stif.getIfstat();
        Statement elsestat = stif.getElsestat();

        if (ifstat.getExprents() != null && ifstat.getExprents().size() == 1
            && elsestat.getExprents() != null && elsestat.getExprents().size() == 1
            && ifstat.getAllSuccessorEdges().size() == 1 && elsestat.getAllSuccessorEdges().size() == 1
            && ifstat.getAllSuccessorEdges().get(0).getDestination() == elsestat.getAllSuccessorEdges().get(0).getDestination()) {

          Exprent ifexpr = ifstat.getExprents().get(0);
          Exprent elseexpr = elsestat.getExprents().get(0);

          if (ifexpr.type == Exprent.EXPRENT_ASSIGNMENT && elseexpr.type == Exprent.EXPRENT_ASSIGNMENT) {
            AssignmentExprent ifas = (AssignmentExprent)ifexpr;
            AssignmentExprent elseas = (AssignmentExprent)elseexpr;

            if (ifas.getLeft().type == Exprent.EXPRENT_VAR && elseas.getLeft().type == Exprent.EXPRENT_VAR) {
              VarExprent ifvar = (VarExprent)ifas.getLeft();
              VarExprent elsevar = (VarExprent)elseas.getLeft();

              if (ifvar.getIndex() == elsevar.getIndex() && ifvar.isStack()) { // ifvar.getIndex() >= VarExprent.STACK_BASE) {

                boolean found = false;

                for (Entry<VarVersionPair, FastSparseSet<Integer>> ent : ssa.getPhi().entrySet()) {
                  if (ent.getKey().var == ifvar.getIndex()) {
                    if (ent.getValue().contains(ifvar.getVersion()) && ent.getValue().contains(elsevar.getVersion())) {
                      found = true;
                      break;
                    }
                  }
                }

                if (found) {
                  List<Exprent> data = new ArrayList<>();
                  data.addAll(stif.getFirst().getExprents());

                  data.add(new AssignmentExprent(ifvar, new FunctionExprent(FunctionExprent.FUNCTION_IIF,
                                                                            Arrays.asList(
                                                                              stif.getHeadexprent().getCondition(),
                                                                              ifas.getRight(),
                                                                              elseas.getRight()), ifheadexpr_bytecode), ifheadexpr_bytecode));
                  stif.setExprents(data);

                  if (stif.getAllSuccessorEdges().isEmpty()) {
                    StatEdge ifedge = ifstat.getAllSuccessorEdges().get(0);
                    StatEdge edge = new StatEdge(ifedge.getType(), stif, ifedge.getDestination());

                    stif.addSuccessor(edge);
                    if (ifedge.closure != null) {
                      ifedge.closure.addLabeledEdge(edge);
                    }
                  }

                  SequenceHelper.destroyAndFlattenStatement(stif);

                  return true;
                }
              }
            }
          }
          else if (ifexpr.type == Exprent.EXPRENT_EXIT && elseexpr.type == Exprent.EXPRENT_EXIT) {
            ExitExprent ifex = (ExitExprent)ifexpr;
            ExitExprent elseex = (ExitExprent)elseexpr;

            if (ifex.getExitType() == elseex.getExitType() && ifex.getValue() != null && elseex.getValue() != null &&
                ifex.getExitType() == ExitExprent.EXIT_RETURN) {

              // throw is dangerous, because of implicit casting to a common superclass
              // e.g. throws IOException and throw true?new RuntimeException():new IOException(); won't work
              if (ifex.getExitType() == ExitExprent.EXIT_THROW &&
                  !ifex.getValue().getExprType().equals(elseex.getValue().getExprType())) {  // note: getExprType unreliable at this point!
                return false;
              }

              List<Exprent> data = new ArrayList<>();
              data.addAll(stif.getFirst().getExprents());

              data.add(new ExitExprent(ifex.getExitType(), new FunctionExprent(FunctionExprent.FUNCTION_IIF,
                                                                               Arrays.asList(
                                                                                 stif.getHeadexprent().getCondition(),
                                                                                 ifex.getValue(),
                                                                                 elseex.getValue()), ifheadexpr_bytecode), ifex.getRetType(), ifheadexpr_bytecode));
              stif.setExprents(data);

              StatEdge retedge = ifstat.getAllSuccessorEdges().get(0);
              stif.addSuccessor(new StatEdge(StatEdge.TYPE_BREAK, stif, retedge.getDestination(),
                                             retedge.closure == stif ? stif.getParent() : retedge.closure));

              SequenceHelper.destroyAndFlattenStatement(stif);

              return true;
            }
          }
        }
      }
    }

    return false;
  }

  static {
    class14Builder.parse(
          "statement type:if iftype:if exprsize:-1\n" +
          " exprent position:head type:if\n" +
          "  exprent type:function functype:eq\n" +
          "   exprent type:field name:$fieldname$\n" +
          "   exprent type:constant consttype:null\n" +
          " statement type:basicblock\n" +
          "  exprent position:-1 type:assignment ret:$assignfield$\n" +
          "   exprent type:var index:$var$\n" +
          "   exprent type:field name:$fieldname$\n" +
          " statement type:sequence statsize:2\n" +
          "  statement type:trycatch\n" +  
          "   statement type:basicblock exprsize:1\n" +
          "    exprent type:assignment\n" +
          "     exprent type:var index:$var$\n" +
          "     exprent type:invocation invclass:java/lang/Class signature:forName(Ljava/lang/String;)Ljava/lang/Class;\n" +
          "      exprent position:0 type:constant consttype:string constvalue:$classname$\n" +
          "   statement type:basicblock exprsize:1\n" +
          "    exprent type:exit exittype:throw\n" + 
          "  statement type:basicblock exprsize:1\n" + 
          "   exprent type:assignment\n" + 
          "    exprent type:field name:$fieldname$ ret:$field$\n" +
          "    exprent type:var index:$var$"
        );
  }
  
  private static boolean collapseInlinedClass14(Statement stat) {

    boolean ret = class14Builder.match(stat);
    if(ret) {
      
      String class_name = (String)class14Builder.getVariableValue("$classname$");
      AssignmentExprent assfirst = (AssignmentExprent)class14Builder.getVariableValue("$assignfield$");
      FieldExprent fieldexpr = (FieldExprent)class14Builder.getVariableValue("$field$");

      assfirst.replaceExprent(assfirst.getRight(), new ConstExprent(VarType.VARTYPE_CLASS, class_name, null));
      
      List<Exprent> data = new ArrayList<>();
      data.addAll(stat.getFirst().getExprents());

      stat.setExprents(data);

      SequenceHelper.destroyAndFlattenStatement(stat);
      
      ClassWrapper wrapper = (ClassWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_WRAPPER);
      if (wrapper != null) {
        wrapper.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(fieldexpr.getName(), fieldexpr.getDescriptor().descriptorString));
      }
      
    }
    
    return ret;
  }
  
}
