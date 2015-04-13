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
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;
import java.util.Map.Entry;

public class ClassReference14Processor {

  public final ExitExprent bodyexprent;

  public final ExitExprent handlerexprent;


  public ClassReference14Processor() {

    InvocationExprent invfor = new InvocationExprent();
    invfor.setName("forName");
    invfor.setClassname("java/lang/Class");
    invfor.setStringDescriptor("(Ljava/lang/String;)Ljava/lang/Class;");
    invfor.setDescriptor(MethodDescriptor.parseDescriptor("(Ljava/lang/String;)Ljava/lang/Class;"));
    invfor.setStatic(true);
    invfor.setLstParameters(Arrays.asList(new Exprent[]{new VarExprent(0, VarType.VARTYPE_STRING, null)}));

    bodyexprent = new ExitExprent(ExitExprent.EXIT_RETURN,
                                  invfor,
                                  VarType.VARTYPE_CLASS, null);

    InvocationExprent constr = new InvocationExprent();
    constr.setName(CodeConstants.INIT_NAME);
    constr.setClassname("java/lang/NoClassDefFoundError");
    constr.setStringDescriptor("()V");
    constr.setFunctype(InvocationExprent.TYP_INIT);
    constr.setDescriptor(MethodDescriptor.parseDescriptor("()V"));

    NewExprent newexpr =
      new NewExprent(new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/NoClassDefFoundError"), new ArrayList<Exprent>(), null);
    newexpr.setConstructor(constr);

    InvocationExprent invcause = new InvocationExprent();
    invcause.setName("initCause");
    invcause.setClassname("java/lang/NoClassDefFoundError");
    invcause.setStringDescriptor("(Ljava/lang/Throwable;)Ljava/lang/Throwable;");
    invcause.setDescriptor(MethodDescriptor.parseDescriptor("(Ljava/lang/Throwable;)Ljava/lang/Throwable;"));
    invcause.setInstance(newexpr);
    invcause.setLstParameters(
      Arrays.asList(new Exprent[]{new VarExprent(2, new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/ClassNotFoundException"), null)}));

    handlerexprent = new ExitExprent(ExitExprent.EXIT_THROW,
                                     invcause,
                                     null, null);
  }


  public void processClassReferences(ClassNode node) {

    ClassWrapper wrapper = node.getWrapper();

    //		int major_version = wrapper.getClassStruct().major_version;
    //		int minor_version = wrapper.getClassStruct().minor_version;
    //
    //		if(major_version > 48 || (major_version == 48 && minor_version > 0)) {
    //			// version 1.5 or above
    //			return;
    //		}

    if (wrapper.getClassStruct().isVersionGE_1_5()) {
      // version 1.5 or above
      return;
    }

    // find the synthetic method Class class$(String) if present
    HashMap<ClassWrapper, MethodWrapper> mapClassMeths = new HashMap<ClassWrapper, MethodWrapper>();
    mapClassMethods(node, mapClassMeths);

    if (mapClassMeths.isEmpty()) {
      return;
    }

    HashSet<ClassWrapper> setFound = new HashSet<ClassWrapper>();
    processClassRec(node, mapClassMeths, setFound);

    if (!setFound.isEmpty()) {
      for (ClassWrapper wrp : setFound) {
        StructMethod mt = mapClassMeths.get(wrp).methodStruct;
        wrp.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
      }
    }
  }

  private static void processClassRec(ClassNode node,
                                      final HashMap<ClassWrapper, MethodWrapper> mapClassMeths,
                                      final HashSet<ClassWrapper> setFound) {

    final ClassWrapper wrapper = node.getWrapper();

    // search code
    for (MethodWrapper meth : wrapper.getMethods()) {

      RootStatement root = meth.root;
      if (root != null) {

        DirectGraph graph = meth.getOrBuildGraph();

        graph.iterateExprents(new DirectGraph.ExprentIterator() {
          public int processExprent(Exprent exprent) {
            for (Entry<ClassWrapper, MethodWrapper> ent : mapClassMeths.entrySet()) {
              if (replaceInvocations(exprent, ent.getKey(), ent.getValue())) {
                setFound.add(ent.getKey());
              }
            }
            return 0;
          }
        });
      }
    }

    // search initializers
    for (int j = 0; j < 2; j++) {
      VBStyleCollection<Exprent, String> initializers =
        j == 0 ? wrapper.getStaticFieldInitializers() : wrapper.getDynamicFieldInitializers();

      for (int i = 0; i < initializers.size(); i++) {
        for (Entry<ClassWrapper, MethodWrapper> ent : mapClassMeths.entrySet()) {
          Exprent exprent = initializers.get(i);
          if (replaceInvocations(exprent, ent.getKey(), ent.getValue())) {
            setFound.add(ent.getKey());
          }

          String cl = isClass14Invocation(exprent, ent.getKey(), ent.getValue());
          if (cl != null) {
            initializers.set(i, new ConstExprent(VarType.VARTYPE_CLASS, cl.replace('.', '/'), exprent.bytecode));
            setFound.add(ent.getKey());
          }
        }
      }
    }

    // iterate nested classes
    for (ClassNode nd : node.nested) {
      processClassRec(nd, mapClassMeths, setFound);
    }
  }

  private void mapClassMethods(ClassNode node, Map<ClassWrapper, MethodWrapper> map) {
    boolean noSynthFlag = DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET);

    ClassWrapper wrapper = node.getWrapper();

    for (MethodWrapper method : wrapper.getMethods()) {
      StructMethod mt = method.methodStruct;

      if ((noSynthFlag || mt.isSynthetic()) &&
          mt.getDescriptor().equals("(Ljava/lang/String;)Ljava/lang/Class;") &&
          mt.hasModifier(CodeConstants.ACC_STATIC)) {

        RootStatement root = method.root;
        if (root != null && root.getFirst().type == Statement.TYPE_TRYCATCH) {
          CatchStatement cst = (CatchStatement)root.getFirst();
          if (cst.getStats().size() == 2 && cst.getFirst().type == Statement.TYPE_BASICBLOCK &&
              cst.getStats().get(1).type == Statement.TYPE_BASICBLOCK &&
              cst.getVars().get(0).getVarType().equals(new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/ClassNotFoundException"))) {

            BasicBlockStatement body = (BasicBlockStatement)cst.getFirst();
            BasicBlockStatement handler = (BasicBlockStatement)cst.getStats().get(1);

            if (body.getExprents().size() == 1 && handler.getExprents().size() == 1) {
              if (bodyexprent.equals(body.getExprents().get(0)) &&
                  handlerexprent.equals(handler.getExprents().get(0))) {
                map.put(wrapper, method);
                break;
              }
            }
          }
        }
      }
    }

    // iterate nested classes
    for (ClassNode nd : node.nested) {
      mapClassMethods(nd, map);
    }
  }


  private static boolean replaceInvocations(Exprent exprent, ClassWrapper wrapper, MethodWrapper meth) {

    boolean res = false;

    while (true) {

      boolean found = false;

      for (Exprent expr : exprent.getAllExprents()) {
        String cl = isClass14Invocation(expr, wrapper, meth);
        if (cl != null) {
          exprent.replaceExprent(expr, new ConstExprent(VarType.VARTYPE_CLASS, cl.replace('.', '/'), expr.bytecode));
          found = true;
          res = true;
          break;
        }

        res |= replaceInvocations(expr, wrapper, meth);
      }

      if (!found) {
        break;
      }
    }

    return res;
  }


  private static String isClass14Invocation(Exprent exprent, ClassWrapper wrapper, MethodWrapper meth) {

    if (exprent.type == Exprent.EXPRENT_FUNCTION) {
      FunctionExprent fexpr = (FunctionExprent)exprent;
      if (fexpr.getFuncType() == FunctionExprent.FUNCTION_IIF) {
        if (fexpr.getLstOperands().get(0).type == Exprent.EXPRENT_FUNCTION) {
          FunctionExprent headexpr = (FunctionExprent)fexpr.getLstOperands().get(0);
          if (headexpr.getFuncType() == FunctionExprent.FUNCTION_EQ) {
            if (headexpr.getLstOperands().get(0).type == Exprent.EXPRENT_FIELD &&
                headexpr.getLstOperands().get(1).type == Exprent.EXPRENT_CONST &&
                ((ConstExprent)headexpr.getLstOperands().get(1)).getConstType().equals(VarType.VARTYPE_NULL)) {

              FieldExprent field = (FieldExprent)headexpr.getLstOperands().get(0);
              ClassNode fieldnode = DecompilerContext.getClassProcessor().getMapRootClasses().get(field.getClassname());

              if (fieldnode != null && fieldnode.classStruct.qualifiedName.equals(wrapper.getClassStruct().qualifiedName)) { // source class
                StructField fd =
                  wrapper.getClassStruct().getField(field.getName(), field.getDescriptor().descriptorString);  // FIXME: can be null! why??

                if (fd != null && fd.hasModifier(CodeConstants.ACC_STATIC) &&
                    (fd.isSynthetic() || DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET))) {

                  if (fexpr.getLstOperands().get(1).type == Exprent.EXPRENT_ASSIGNMENT && fexpr.getLstOperands().get(2).equals(field)) {
                    AssignmentExprent asexpr = (AssignmentExprent)fexpr.getLstOperands().get(1);

                    if (asexpr.getLeft().equals(field) && asexpr.getRight().type == Exprent.EXPRENT_INVOCATION) {
                      InvocationExprent invexpr = (InvocationExprent)asexpr.getRight();

                      if (invexpr.getClassname().equals(wrapper.getClassStruct().qualifiedName) &&
                          invexpr.getName().equals(meth.methodStruct.getName()) &&
                          invexpr.getStringDescriptor().equals(meth.methodStruct.getDescriptor())) {

                        if (invexpr.getLstParameters().get(0).type == Exprent.EXPRENT_CONST) {
                          wrapper.getHiddenMembers()
                            .add(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));  // hide synthetic field
                          return ((ConstExprent)invexpr.getLstParameters().get(0)).getValue().toString();
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

    return null;
  }
}
