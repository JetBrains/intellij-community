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
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.SecondaryFunctionsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AssertProcessor {

  private static final VarType CLASS_ASSERTION_ERROR = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/AssertionError");

  public static void buildAssertions(ClassNode node) {

    ClassWrapper wrapper = node.getWrapper();

    StructField field = findAssertionField(node);

    if (field != null) {

      String key = InterpreterUtil.makeUniqueKey(field.getName(), field.getDescriptor());

      boolean res = false;

      for (MethodWrapper meth : wrapper.getMethods()) {
        RootStatement root = meth.root;
        if (root != null) {
          res |= replaceAssertions(root, wrapper.getClassStruct().qualifiedName, key);
        }
      }

      if (res) {
        // hide the helper field
        wrapper.getHiddenMembers().add(key);
      }
    }
  }

  private static StructField findAssertionField(ClassNode node) {

    ClassWrapper wrapper = node.getWrapper();

    boolean noSynthFlag = DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET);

    for (StructField fd : wrapper.getClassStruct().getFields()) {

      String keyField = InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor());

      // initializer exists
      if (wrapper.getStaticFieldInitializers().containsKey(keyField)) {

        // access flags set
        if (fd.hasModifier(CodeConstants.ACC_STATIC) && fd.hasModifier(CodeConstants.ACC_FINAL) && (noSynthFlag || fd.isSynthetic())) {

          // field type boolean
          FieldDescriptor fdescr = FieldDescriptor.parseDescriptor(fd.getDescriptor());
          if (VarType.VARTYPE_BOOLEAN.equals(fdescr.type)) {

            Exprent initializer = wrapper.getStaticFieldInitializers().getWithKey(keyField);
            if (initializer.type == Exprent.EXPRENT_FUNCTION) {
              FunctionExprent fexpr = (FunctionExprent)initializer;

              if (fexpr.getFuncType() == FunctionExprent.FUNCTION_BOOL_NOT &&
                  fexpr.getLstOperands().get(0).type == Exprent.EXPRENT_INVOCATION) {

                InvocationExprent invexpr = (InvocationExprent)fexpr.getLstOperands().get(0);

                if (invexpr.getInstance() != null &&
                    invexpr.getInstance().type == Exprent.EXPRENT_CONST &&
                    "desiredAssertionStatus".equals(invexpr.getName()) &&
                    "java/lang/Class".equals(invexpr.getClassname()) &&
                    invexpr.getLstParameters().isEmpty()) {

                  ConstExprent cexpr = (ConstExprent)invexpr.getInstance();
                  if (VarType.VARTYPE_CLASS.equals(cexpr.getConstType())) {

                    ClassNode nd = node;
                    while (nd != null) {
                      if (nd.getWrapper().getClassStruct().qualifiedName.equals(cexpr.getValue())) {
                        break;
                      }
                      nd = nd.parent;
                    }

                    if (nd != null) { // found enclosing class with the same name
                      return fd;
                    }
                  }
                }
              }
            }
          }
        }
      }
    }


    return null;
  }


  private static boolean replaceAssertions(Statement statement, String classname, String key) {

    boolean res = false;

    for (Statement st : statement.getStats()) {
      res |= replaceAssertions(st, classname, key);
    }

    boolean replaced = true;
    while (replaced) {
      replaced = false;

      for (Statement st : statement.getStats()) {
        if (st.type == Statement.TYPE_IF) {
          if (replaceAssertion(statement, (IfStatement)st, classname, key)) {
            replaced = true;
            break;
          }
        }
      }

      res |= replaced;
    }

    return res;
  }

  private static boolean replaceAssertion(Statement parent, IfStatement stat, String classname, String key) {

    Statement ifstat = stat.getIfstat();
    InvocationExprent throwError = isAssertionError(ifstat);

    if (throwError == null) {
      return false;
    }

    Object[] exprres = getAssertionExprent(stat.getHeadexprent().getCondition().copy(), classname, key);
    if (!(Boolean)exprres[1]) {
      return false;
    }

    List<Exprent> lstParams = new ArrayList<>();

    Exprent ascond = null, retcond = null;
    if (exprres[0] != null) {
      ascond = new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT, (Exprent)exprres[0], throwError.bytecode);
      retcond = SecondaryFunctionsHelper.propagateBoolNot(ascond);
    }

    lstParams.add(retcond == null ? ascond : retcond);
    if (!throwError.getLstParameters().isEmpty()) {
      lstParams.add(throwError.getLstParameters().get(0));
    }

    AssertExprent asexpr = new AssertExprent(lstParams);

    Statement newstat = new BasicBlockStatement(new BasicBlock(
      DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
    newstat.setExprents(Arrays.asList(new Exprent[]{asexpr}));

    Statement first = stat.getFirst();

    if (stat.iftype == IfStatement.IFTYPE_IFELSE || (first.getExprents() != null &&
                                                     !first.getExprents().isEmpty())) {

      first.removeSuccessor(stat.getIfEdge());
      first.removeSuccessor(stat.getElseEdge());

      List<Statement> lstStatements = new ArrayList<>();
      if (first.getExprents() != null && !first.getExprents().isEmpty()) {
        lstStatements.add(first);
      }
      lstStatements.add(newstat);
      if (stat.iftype == IfStatement.IFTYPE_IFELSE) {
        lstStatements.add(stat.getElsestat());
      }

      SequenceStatement sequence = new SequenceStatement(lstStatements);
      sequence.setAllParent();

      for (int i = 0; i < sequence.getStats().size() - 1; i++) {
        sequence.getStats().get(i).addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR,
                                                             sequence.getStats().get(i), sequence.getStats().get(i + 1)));
      }

      if (stat.iftype == IfStatement.IFTYPE_IFELSE) {
        Statement ifelse = stat.getElsestat();

        List<StatEdge> lstSuccs = ifelse.getAllSuccessorEdges();
        if (!lstSuccs.isEmpty()) {
          StatEdge endedge = lstSuccs.get(0);
          if (endedge.closure == stat) {
            sequence.addLabeledEdge(endedge);
          }
        }
      }

      newstat = sequence;
    }

    newstat.getVarDefinitions().addAll(stat.getVarDefinitions());
    parent.replaceStatement(stat, newstat);

    return true;
  }

  private static InvocationExprent isAssertionError(Statement stat) {

    if (stat == null || stat.getExprents() == null || stat.getExprents().size() != 1) {
      return null;
    }

    Exprent expr = stat.getExprents().get(0);

    if (expr.type == Exprent.EXPRENT_EXIT) {
      ExitExprent exexpr = (ExitExprent)expr;
      if (exexpr.getExitType() == ExitExprent.EXIT_THROW && exexpr.getValue().type == Exprent.EXPRENT_NEW) {
        NewExprent nexpr = (NewExprent)exexpr.getValue();
        if (CLASS_ASSERTION_ERROR.equals(nexpr.getNewType()) && nexpr.getConstructor() != null) {
          return nexpr.getConstructor();
        }
      }
    }

    return null;
  }

  private static Object[] getAssertionExprent(Exprent exprent, String classname, String key) {

    if (exprent.type == Exprent.EXPRENT_FUNCTION) {
      FunctionExprent fexpr = (FunctionExprent)exprent;
      if (fexpr.getFuncType() == FunctionExprent.FUNCTION_CADD) {

        for (int i = 0; i < 2; i++) {
          Exprent param = fexpr.getLstOperands().get(i);

          if (isAssertionField(param, classname, key)) {
            return new Object[]{fexpr.getLstOperands().get(1 - i), true};
          }
        }

        for (int i = 0; i < 2; i++) {
          Exprent param = fexpr.getLstOperands().get(i);

          Object[] res = getAssertionExprent(param, classname, key);
          if ((Boolean)res[1]) {
            if (param != res[0]) {
              fexpr.getLstOperands().set(i, (Exprent)res[0]);
            }
            return new Object[]{fexpr, true};
          }
        }
      }
      else if (isAssertionField(fexpr, classname, key)) {
        // assert false;
        return new Object[]{null, true};
      }
    }

    return new Object[]{exprent, false};
  }

  private static boolean isAssertionField(Exprent exprent, String classname, String key) {

    if (exprent.type == Exprent.EXPRENT_FUNCTION) {
      FunctionExprent fparam = (FunctionExprent)exprent;
      if (fparam.getFuncType() == FunctionExprent.FUNCTION_BOOL_NOT &&
          fparam.getLstOperands().get(0).type == Exprent.EXPRENT_FIELD) {
        FieldExprent fdparam = (FieldExprent)fparam.getLstOperands().get(0);
        if (classname.equals(fdparam.getClassname())
            && key.equals(InterpreterUtil.makeUniqueKey(fdparam.getName(), fdparam.getDescriptor().descriptorString))) {
          return true;
        }
      }
    }

    return false;
  }
}
