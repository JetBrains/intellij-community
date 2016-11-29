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
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarTypeProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructEnclosingMethodAttribute;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.*;
import java.util.Map.Entry;

public class NestedClassProcessor {

  public void processClass(ClassNode root, ClassNode node) {
    // hide synthetic lambda content methods
    if (node.type == ClassNode.CLASS_LAMBDA && !node.lambdaInformation.is_method_reference) {
      ClassNode node_content = DecompilerContext.getClassProcessor().getMapRootClasses().get(node.classStruct.qualifiedName);
      if (node_content != null && node_content.getWrapper() != null) {
        node_content.getWrapper().getHiddenMembers().add(node.lambdaInformation.content_method_key);
      }
    }

    if (node.nested.isEmpty()) {
      return;
    }

    if (node.type != ClassNode.CLASS_LAMBDA) {
      computeLocalVarsAndDefinitions(node);

      // for each local or anonymous class ensure not empty enclosing method
      checkNotFoundClasses(root, node);
    }

    int nameless = 0, synthetics = 0;
    for (ClassNode child : node.nested) {
      StructClass cl = child.classStruct;
      // ensure not-empty class name
      if ((child.type == ClassNode.CLASS_LOCAL || child.type == ClassNode.CLASS_MEMBER) && child.simpleName == null) {
        if ((child.access & CodeConstants.ACC_SYNTHETIC) != 0 || cl.isSynthetic()) {
          child.simpleName = "SyntheticClass_" + (++synthetics);
        }
        else {
          String message = "Nameless local or member class " + cl.qualifiedName + "!";
          DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
          child.simpleName = "NamelessClass_" + (++nameless);
        }
        child.namelessConstructorStub = !cl.hasModifier(CodeConstants.ACC_STATIC) && cl.getMethods().size() + cl.getFields().size() == 0;
      }
      else if (child.type == ClassNode.CLASS_ANONYMOUS && (child.access & CodeConstants.ACC_SYNTHETIC) != 0 || cl.isSynthetic()) {
        child.namelessConstructorStub = !cl.hasModifier(CodeConstants.ACC_STATIC) && cl.getMethods().size() + cl.getFields().size() == 0;
      }
    }

    for (ClassNode child : node.nested) {
      if (child.type == ClassNode.CLASS_LAMBDA) {
        setLambdaVars(node, child);
      }
      else if (child.type != ClassNode.CLASS_MEMBER || (child.access & CodeConstants.ACC_STATIC) == 0) {
        insertLocalVars(node, child);

        if (child.type == ClassNode.CLASS_LOCAL) {
          setLocalClassDefinition(node.getWrapper().getMethods().getWithKey(child.enclosingMethod), child);
        }
      }
    }

    for (ClassNode child : node.nested) {
      processClass(root, child);
    }
  }

  private static void setLambdaVars(ClassNode parent, ClassNode child) {
    if (child.lambdaInformation.is_method_reference) { // method reference, no code and no parameters
      return;
    }

    MethodWrapper method = parent.getWrapper().getMethods().getWithKey(child.lambdaInformation.content_method_key);
    final MethodWrapper enclosingMethod = parent.getWrapper().getMethods().getWithKey(child.enclosingMethod);

    MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(child.lambdaInformation.method_descriptor);
    final MethodDescriptor md_content = MethodDescriptor.parseDescriptor(child.lambdaInformation.content_method_descriptor);

    final int vars_count = md_content.params.length - md_lambda.params.length;
    //		if(vars_count < 0) { // should not happen, but just in case...
    //			vars_count = 0;
    //		}

    final boolean is_static_lambda_content = child.lambdaInformation.is_content_method_static;

    String parent_class_name = parent.getWrapper().getClassStruct().qualifiedName;
    String lambda_class_name = child.simpleName;

    final VarType lambda_class_type = new VarType(lambda_class_name, true);

    // this pointer
    if (!is_static_lambda_content && DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS)) {
      method.varproc.getThisVars().put(new VarVersionPair(0, 0), parent_class_name);
      method.varproc.setVarName(new VarVersionPair(0, 0), parent.simpleName + ".this");
    }

    final Map<VarVersionPair, String> mapNewNames = new HashMap<>();

    enclosingMethod.getOrBuildGraph().iterateExprents(new DirectGraph.ExprentIterator() {
      @Override
      public int processExprent(Exprent exprent) {
        List<Exprent> lst = exprent.getAllExprents(true);
        lst.add(exprent);

        for (Exprent expr : lst) {
          if (expr.type == Exprent.EXPRENT_NEW) {
            NewExprent new_expr = (NewExprent)expr;

            if (new_expr.isLambda() && lambda_class_type.equals(new_expr.getNewType())) {
              InvocationExprent inv_dynamic = new_expr.getConstructor();

              int param_index = is_static_lambda_content ? 0 : 1;
              int varIndex = is_static_lambda_content ? 0 : 1;

              for (int i = 0; i < vars_count; ++i) {
                Exprent param = inv_dynamic.getLstParameters().get(param_index + i);

                if (param.type == Exprent.EXPRENT_VAR) {
                  VarVersionPair pair = new VarVersionPair((VarExprent)param);
                  String name = enclosingMethod.varproc.getVarName(pair);
                  mapNewNames.put(new VarVersionPair(varIndex, 0), name);
                }

                varIndex += md_content.params[i].stackSize;
              }
            }
          }
        }

        return 0;
      }
    });

    // update names of local variables
    Set<String> setNewOuterNames = new HashSet<>(mapNewNames.values());
    setNewOuterNames.removeAll(method.setOuterVarNames);

    method.varproc.refreshVarNames(new VarNamesCollector(setNewOuterNames));
    method.setOuterVarNames.addAll(setNewOuterNames);

    for (Entry<VarVersionPair, String> entry : mapNewNames.entrySet()) {
      method.varproc.setVarName(entry.getKey(), entry.getValue());
    }
  }

  private static void checkNotFoundClasses(ClassNode root, ClassNode node) {
    List<ClassNode> copy = new ArrayList<>(node.nested);

    for (ClassNode child : copy) {
      if (child.classStruct.hasModifier(CodeConstants.ACC_SYNTHETIC)) {
        continue;
      }

      if ((child.type == ClassNode.CLASS_LOCAL || child.type == ClassNode.CLASS_ANONYMOUS) && child.enclosingMethod == null) {
        Set<String> setEnclosing = child.enclosingClasses;

        if (!setEnclosing.isEmpty()) {
          StructEnclosingMethodAttribute attr =
            (StructEnclosingMethodAttribute)child.classStruct.getAttributes().getWithKey("EnclosingMethod");
          if (attr != null &&
              attr.getMethodName() != null &&
              node.classStruct.qualifiedName.equals(attr.getClassName()) &&
              node.classStruct.getMethod(attr.getMethodName(), attr.getMethodDescriptor()) != null) {
            child.enclosingMethod = InterpreterUtil.makeUniqueKey(attr.getMethodName(), attr.getMethodDescriptor());
            continue;
          }
        }

        node.nested.remove(child);
        child.parent = null;
        setEnclosing.remove(node.classStruct.qualifiedName);

        boolean hasEnclosing = !setEnclosing.isEmpty() && insertNestedClass(root, child);

        if (!hasEnclosing) {
          if (child.type == ClassNode.CLASS_ANONYMOUS) {
            String message = "Unreferenced anonymous class " + child.classStruct.qualifiedName + "!";
            DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
          }
          else if (child.type == ClassNode.CLASS_LOCAL) {
            String message = "Unreferenced local class " + child.classStruct.qualifiedName + "!";
            DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
          }
        }
      }
    }
  }

  private static boolean insertNestedClass(ClassNode root, ClassNode child) {
    Set<String> setEnclosing = child.enclosingClasses;

    LinkedList<ClassNode> stack = new LinkedList<>();
    stack.add(root);

    while (!stack.isEmpty()) {
      ClassNode node = stack.removeFirst();

      if (setEnclosing.contains(node.classStruct.qualifiedName)) {
        node.nested.add(child);
        child.parent = node;

        return true;
      }

      // note: ordered list
      stack.addAll(node.nested);
    }

    return false;
  }

  private static void computeLocalVarsAndDefinitions(final ClassNode node) {
    // local var masks
    // class name, constructor descriptor, field mask
    final Map<String, Map<String, List<VarFieldPair>>> mapVarMasks = new HashMap<>();

    int clTypes = 0;

    for (ClassNode nd : node.nested) {
      if (nd.classStruct.hasModifier(CodeConstants.ACC_SYNTHETIC)) {
        continue;
      }

      if (nd.type != ClassNode.CLASS_LAMBDA &&
          (nd.access & CodeConstants.ACC_STATIC) == 0 &&
          (nd.access & CodeConstants.ACC_INTERFACE) == 0) {
        clTypes |= nd.type;

        Map<String, List<VarFieldPair>> mask = getMaskLocalVars(nd.getWrapper());
        if (mask.isEmpty()) {
          String message = "Nested class " + nd.classStruct.qualifiedName + " has no constructor!";
          DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
        }
        else {
          mapVarMasks.put(nd.classStruct.qualifiedName, mask);
        }
      }
    }

    // local var masks
    final Map<String, Map<String, List<VarFieldPair>>> mapVarFieldPairs = new HashMap<>();

    if (clTypes != ClassNode.CLASS_MEMBER) {
      // iterate enclosing class
      for (final MethodWrapper method : node.getWrapper().getMethods()) {
        if (method.root != null) { // neither abstract, nor native
          method.getOrBuildGraph().iterateExprents(new DirectGraph.ExprentIterator() {
            @Override
            public int processExprent(Exprent exprent) {
              List<Exprent> lst = exprent.getAllExprents(true);
              lst.add(exprent);

              for (Exprent expr : lst) {
                if (expr.type == Exprent.EXPRENT_NEW) {
                  InvocationExprent constructor = ((NewExprent)expr).getConstructor();

                  if (constructor != null && mapVarMasks.containsKey(constructor.getClassname())) { // non-static inner class constructor
                    String refClassName = constructor.getClassname();
                    ClassNode nestedClassNode = node.getClassNode(refClassName);

                    if (nestedClassNode.type != ClassNode.CLASS_MEMBER) {
                      List<VarFieldPair> mask = mapVarMasks.get(refClassName).get(constructor.getStringDescriptor());

                      if (!mapVarFieldPairs.containsKey(refClassName)) {
                        mapVarFieldPairs.put(refClassName, new HashMap<>());
                      }

                      List<VarFieldPair> lstTemp = new ArrayList<>();

                      for (int i = 0; i < mask.size(); i++) {
                        Exprent param = constructor.getLstParameters().get(i);
                        VarFieldPair pair = null;

                        if (param.type == Exprent.EXPRENT_VAR && mask.get(i) != null) {
                          VarVersionPair varPair = new VarVersionPair((VarExprent)param);

                          // FIXME: flags of variables are wrong! Correct the entire functionality.
                          // if(method.varproc.getVarFinal(varPair) != VarTypeProcessor.VAR_NON_FINAL) {
                          pair = new VarFieldPair(mask.get(i).fieldKey, varPair);
                          // }
                        }

                        lstTemp.add(pair);
                      }

                      List<VarFieldPair> pairMask = mapVarFieldPairs.get(refClassName).get(constructor.getStringDescriptor());
                      if (pairMask == null) {
                        pairMask = lstTemp;
                      }
                      else {
                        for (int i = 0; i < pairMask.size(); i++) {
                          if (!InterpreterUtil.equalObjects(pairMask.get(i), lstTemp.get(i))) {
                            pairMask.set(i, null);
                          }
                        }
                      }

                      mapVarFieldPairs.get(refClassName).put(constructor.getStringDescriptor(), pairMask);
                      nestedClassNode.enclosingMethod =
                        InterpreterUtil.makeUniqueKey(method.methodStruct.getName(), method.methodStruct.getDescriptor());
                    }
                  }
                }
              }

              return 0;
            }
          });
        }
      }
    }

    // merge var masks
    for (Entry<String, Map<String, List<VarFieldPair>>> enclosing : mapVarMasks.entrySet()) {
      ClassNode nestedNode = node.getClassNode(enclosing.getKey());

      // intersection
      List<VarFieldPair> interPairMask = null;
      // merge referenced constructors
      if (mapVarFieldPairs.containsKey(enclosing.getKey())) {
        for (List<VarFieldPair> mask : mapVarFieldPairs.get(enclosing.getKey()).values()) {
          if (interPairMask == null) {
            interPairMask = new ArrayList<>(mask);
          }
          else {
            mergeListSignatures(interPairMask, mask, false);
          }
        }
      }

      List<VarFieldPair> interMask = null;
      // merge all constructors
      for (List<VarFieldPair> mask : enclosing.getValue().values()) {
        if (interMask == null) {
          interMask = new ArrayList<>(mask);
        }
        else {
          mergeListSignatures(interMask, mask, false);
        }
      }

      if (interPairMask == null) { // member or local and never instantiated
        interPairMask = interMask != null ? new ArrayList<>(interMask) : new ArrayList<>();

        boolean found = false;

        for (int i = 0; i < interPairMask.size(); i++) {
          if (interPairMask.get(i) != null) {
            if (found) {
              interPairMask.set(i, null);
            }
            found = true;
          }
        }
      }

      mergeListSignatures(interPairMask, interMask, true);

      for (VarFieldPair pair : interPairMask) {
        if (pair != null && pair.fieldKey.length() > 0) {
          nestedNode.mapFieldsToVars.put(pair.fieldKey, pair.varPair);
        }
      }

      // set resulting constructor signatures
      for (Entry<String, List<VarFieldPair>> entry : enclosing.getValue().entrySet()) {
        mergeListSignatures(entry.getValue(), interPairMask, false);

        MethodWrapper method = nestedNode.getWrapper().getMethodWrapper(CodeConstants.INIT_NAME, entry.getKey());
        method.signatureFields = new ArrayList<>();

        for (VarFieldPair pair : entry.getValue()) {
          method.signatureFields.add(pair == null ? null : pair.varPair);
        }
      }
    }
  }

  private static void insertLocalVars(ClassNode parent, final ClassNode child) {
    // enclosing method, is null iff member class
    MethodWrapper enclosingMethod = parent.getWrapper().getMethods().getWithKey(child.enclosingMethod);

    // iterate all child methods
    for (final MethodWrapper method : child.getWrapper().getMethods()) {
      if (method.root != null) { // neither abstract nor native
        Map<VarVersionPair, String> mapNewNames = new HashMap<>();  // local var names
        Map<VarVersionPair, VarType> mapNewTypes = new HashMap<>();  // local var types

        final Map<Integer, VarVersionPair> mapParamsToNewVars = new HashMap<>();
        if (method.signatureFields != null) {
          int index = 0, varIndex = 1;
          MethodDescriptor md = MethodDescriptor.parseDescriptor(method.methodStruct.getDescriptor());

          for (VarVersionPair pair : method.signatureFields) {
            if (pair != null) {
              VarVersionPair newVar = new VarVersionPair(method.counter.getCounterAndIncrement(CounterContainer.VAR_COUNTER), 0);

              mapParamsToNewVars.put(varIndex, newVar);

              String varName = null;
              VarType varType = null;

              if (child.type != ClassNode.CLASS_MEMBER) {
                varName = enclosingMethod.varproc.getVarName(pair);
                varType = enclosingMethod.varproc.getVarType(pair);

                enclosingMethod.varproc.setVarFinal(pair, VarTypeProcessor.VAR_EXPLICIT_FINAL);
              }

              if (pair.var == -1 || "this".equals(varName)) {
                if (parent.simpleName == null) {
                  // anonymous enclosing class, no access to this
                  varName = VarExprent.VAR_NAMELESS_ENCLOSURE;
                }
                else {
                  varName = parent.simpleName + ".this";
                }
                method.varproc.getThisVars().put(newVar, parent.classStruct.qualifiedName);
              }

              mapNewNames.put(newVar, varName);
              mapNewTypes.put(newVar, varType);
            }

            varIndex += md.params[index++].stackSize;
          }
        }

        final Map<String, VarVersionPair> mapFieldsToNewVars = new HashMap<>();
        for (ClassNode classNode = child; classNode != null; classNode = classNode.parent) {
          for (Entry<String, VarVersionPair> entry : classNode.mapFieldsToVars.entrySet()) {
            VarVersionPair newVar = new VarVersionPair(method.counter.getCounterAndIncrement(CounterContainer.VAR_COUNTER), 0);

            mapFieldsToNewVars.put(InterpreterUtil.makeUniqueKey(classNode.classStruct.qualifiedName, entry.getKey()), newVar);

            String varName = null;
            VarType varType = null;

            if (classNode.type != ClassNode.CLASS_MEMBER) {
              MethodWrapper enclosing_method = classNode.parent.getWrapper().getMethods().getWithKey(classNode.enclosingMethod);

              varName = enclosing_method.varproc.getVarName(entry.getValue());
              varType = enclosing_method.varproc.getVarType(entry.getValue());

              enclosing_method.varproc.setVarFinal(entry.getValue(), VarTypeProcessor.VAR_EXPLICIT_FINAL);
            }

            if (entry.getValue().var == -1 || "this".equals(varName)) {
              if (classNode.parent.simpleName == null) {
                // anonymous enclosing class, no access to this
                varName = VarExprent.VAR_NAMELESS_ENCLOSURE;
              }
              else {
                varName = classNode.parent.simpleName + ".this";
              }
              method.varproc.getThisVars().put(newVar, classNode.parent.classStruct.qualifiedName);
            }

            mapNewNames.put(newVar, varName);
            mapNewTypes.put(newVar, varType);

            // hide synthetic field
            if (classNode == child) { // fields higher up the chain were already handled with their classes
              StructField fd = child.classStruct.getFields().getWithKey(entry.getKey());
              child.getWrapper().getHiddenMembers().add(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
            }
          }
        }

        Set<String> setNewOuterNames = new HashSet<>(mapNewNames.values());
        setNewOuterNames.removeAll(method.setOuterVarNames);

        method.varproc.refreshVarNames(new VarNamesCollector(setNewOuterNames));
        method.setOuterVarNames.addAll(setNewOuterNames);

        for (Entry<VarVersionPair, String> entry : mapNewNames.entrySet()) {
          VarVersionPair pair = entry.getKey();
          VarType type = mapNewTypes.get(pair);

          method.varproc.setVarName(pair, entry.getValue());
          if (type != null) {
            method.varproc.setVarType(pair, type);
          }
        }

        method.getOrBuildGraph().iterateExprents(new DirectGraph.ExprentIterator() {
          @Override
          public int processExprent(Exprent exprent) {
            if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
              AssignmentExprent assignExpr = (AssignmentExprent)exprent;
              if (assignExpr.getLeft().type == Exprent.EXPRENT_FIELD) {
                FieldExprent fExpr = (FieldExprent)assignExpr.getLeft();
                String qName = child.classStruct.qualifiedName;
                if (fExpr.getClassname().equals(qName) &&  // process this class only
                    mapFieldsToNewVars.containsKey(InterpreterUtil.makeUniqueKey(qName, fExpr.getName(), fExpr.getDescriptor().descriptorString))) {
                  return 2;
                }
              }
            }

            if (child.type == ClassNode.CLASS_ANONYMOUS &&
                CodeConstants.INIT_NAME.equals(method.methodStruct.getName()) &&
                exprent.type == Exprent.EXPRENT_INVOCATION) {
              InvocationExprent invokeExpr = (InvocationExprent)exprent;
              if (invokeExpr.getFunctype() == InvocationExprent.TYP_INIT) {
                // invocation of the super constructor in an anonymous class
                child.superInvocation = invokeExpr; // FIXME: save original names of parameters
                return 2;
              }
            }

            replaceExprent(exprent);

            return 0;
          }

          private Exprent replaceExprent(Exprent exprent) {
            if (exprent.type == Exprent.EXPRENT_VAR) {
              int varIndex = ((VarExprent)exprent).getIndex();
              if (mapParamsToNewVars.containsKey(varIndex)) {
                VarVersionPair newVar = mapParamsToNewVars.get(varIndex);
                method.varproc.getExternalVars().add(newVar);
                return new VarExprent(newVar.var, method.varproc.getVarType(newVar), method.varproc);
              }
            }
            else if (exprent.type == Exprent.EXPRENT_FIELD) {
              FieldExprent fExpr = (FieldExprent)exprent;
              String key = InterpreterUtil.makeUniqueKey(fExpr.getClassname(), fExpr.getName(), fExpr.getDescriptor().descriptorString);
              if (mapFieldsToNewVars.containsKey(key)) {
                //if(fExpr.getClassname().equals(child.classStruct.qualifiedName) &&
                //		mapFieldsToNewVars.containsKey(key)) {
                VarVersionPair newVar = mapFieldsToNewVars.get(key);
                method.varproc.getExternalVars().add(newVar);
                return new VarExprent(newVar.var, method.varproc.getVarType(newVar), method.varproc);
              }
            }

            boolean replaced = true;
            while (replaced) {
              replaced = false;

              for (Exprent expr : exprent.getAllExprents()) {
                Exprent retExpr = replaceExprent(expr);
                if (retExpr != null) {
                  exprent.replaceExprent(expr, retExpr);
                  replaced = true;
                  break;
                }
              }
            }

            return null;
          }
        });
      }
    }
  }

  private static Map<String, List<VarFieldPair>> getMaskLocalVars(ClassWrapper wrapper) {
    Map<String, List<VarFieldPair>> mapMasks = new HashMap<>();

    StructClass cl = wrapper.getClassStruct();

    // iterate over constructors
    for (StructMethod mt : cl.getMethods()) {
      if (CodeConstants.INIT_NAME.equals(mt.getName())) {
        MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
        MethodWrapper method = wrapper.getMethodWrapper(CodeConstants.INIT_NAME, mt.getDescriptor());
        DirectGraph graph = method.getOrBuildGraph();

        if (graph != null) { // something gone wrong, should not be null
          List<VarFieldPair> fields = new ArrayList<>();

          int varIndex = 1;
          for (int i = 0; i < md.params.length; i++) {  // no static methods allowed
            String keyField = getEnclosingVarField(cl, method, graph, varIndex);
            fields.add(keyField == null ? null : new VarFieldPair(keyField, new VarVersionPair(-1, 0))); // TODO: null?
            varIndex += md.params[i].stackSize;
          }

          mapMasks.put(mt.getDescriptor(), fields);
        }
      }
    }

    return mapMasks;
  }

  private static String getEnclosingVarField(StructClass cl, MethodWrapper method, DirectGraph graph, int index) {
    String field = "";

    // parameter variable final
    if (method.varproc.getVarFinal(new VarVersionPair(index, 0)) == VarTypeProcessor.VAR_NON_FINAL) {
      return null;
    }

    boolean noSynthFlag = DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET);

    // no loop at the begin
    DirectNode firstNode = graph.first;
    if (firstNode.preds.isEmpty()) {
      // assignment to a synthetic field?
      for (Exprent exprent : firstNode.exprents) {
        if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
          AssignmentExprent assignExpr = (AssignmentExprent)exprent;
          if (assignExpr.getRight().type == Exprent.EXPRENT_VAR &&
              ((VarExprent)assignExpr.getRight()).getIndex() == index &&
              assignExpr.getLeft().type == Exprent.EXPRENT_FIELD) {
            FieldExprent left = (FieldExprent)assignExpr.getLeft();
            StructField fd = cl.getField(left.getName(), left.getDescriptor().descriptorString);

            if (fd != null && cl.qualifiedName.equals(left.getClassname()) &&
                fd.hasModifier(CodeConstants.ACC_FINAL) &&
                (fd.isSynthetic() || (noSynthFlag && fd.hasModifier(CodeConstants.ACC_PRIVATE)))) {
              // local (== not inherited) field
              field = InterpreterUtil.makeUniqueKey(left.getName(), left.getDescriptor().descriptorString);
              break;
            }
          }
        }
      }
    }

    return field;
  }

  private static void mergeListSignatures(List<VarFieldPair> first, List<VarFieldPair> second, boolean both) {
    int i = 1;

    while (true) {
      if (first.size() <= i || second.size() <= i) {
        break;
      }

      VarFieldPair fObj = first.get(first.size() - i);
      VarFieldPair sObj = second.get(second.size() - i);

      if (!isEqual(both, fObj, sObj)) {
        first.set(first.size() - i, null);
        if (both) {
          second.set(second.size() - i, null);
        }
      }
      else if (fObj != null) {
        if (fObj.varPair.var == -1) {
          fObj.varPair = sObj.varPair;
        }
        else {
          sObj.varPair = fObj.varPair;
        }
      }

      i++;
    }

    for (int j = 1; j <= first.size() - i; j++) {
      first.set(j, null);
    }

    if (both) {
      for (int j = 1; j <= second.size() - i; j++) {
        second.set(j, null);
      }
    }

    // first
    if (first.isEmpty()) {
      if (!second.isEmpty() && both) {
        second.set(0, null);
      }
    }
    else if (second.isEmpty()) {
      first.set(0, null);
    }
    else {
      VarFieldPair fObj = first.get(0);
      VarFieldPair sObj = second.get(0);

      if (!isEqual(both, fObj, sObj)) {
        first.set(0, null);
        if (both) {
          second.set(0, null);
        }
      }
      else if (fObj != null) {
        if (fObj.varPair.var == -1) {
          fObj.varPair = sObj.varPair;
        }
        else {
          sObj.varPair = fObj.varPair;
        }
      }
    }
  }

  private static boolean isEqual(boolean both, VarFieldPair fObj, VarFieldPair sObj) {
    boolean eq;
    if (fObj == null || sObj == null) {
      eq = (fObj == sObj);
    }
    else {
      eq = true;
      if (fObj.fieldKey.length() == 0) {
        fObj.fieldKey = sObj.fieldKey;
      }
      else if (sObj.fieldKey.length() == 0) {
        if (both) {
          sObj.fieldKey = fObj.fieldKey;
        }
      }
      else {
        eq = fObj.fieldKey.equals(sObj.fieldKey);
      }
    }
    return eq;
  }

  private static void setLocalClassDefinition(MethodWrapper method, ClassNode node) {
    RootStatement root = method.root;

    Set<Statement> setStats = new HashSet<>();
    VarType classType = new VarType(node.classStruct.qualifiedName, true);

    Statement statement = getDefStatement(root, classType, setStats);
    if (statement == null) {
      // unreferenced local class
      statement = root.getFirst();
    }

    Statement first = findFirstBlock(statement, setStats);

    List<Exprent> lst;
    //noinspection Duplicates
    if (first == null) {
      lst = statement.getVarDefinitions();
    }
    else if (first.getExprents() == null) {
      lst = first.getVarDefinitions();
    }
    else {
      lst = first.getExprents();
    }

    int addIndex = 0;
    for (Exprent expr : lst) {
      if (searchForClass(expr, classType)) {
        break;
      }
      addIndex++;
    }

    VarExprent var = new VarExprent(method.counter.getCounterAndIncrement(CounterContainer.VAR_COUNTER), classType, method.varproc);
    var.setDefinition(true);
    var.setClassDef(true);

    lst.add(addIndex, var);
  }

  private static Statement findFirstBlock(Statement stat, Set<Statement> setStats) {
    LinkedList<Statement> stack = new LinkedList<>();
    stack.add(stat);

    while (!stack.isEmpty()) {
      Statement st = stack.remove(0);

      if (stack.isEmpty() || setStats.contains(st)) {
        if (st.isLabeled() && !stack.isEmpty() || st.getExprents() != null) {
          return st;
        }

        stack.clear();

        //noinspection Duplicates
        switch (st.type) {
          case Statement.TYPE_SEQUENCE:
            stack.addAll(0, st.getStats());
            break;
          case Statement.TYPE_IF:
          case Statement.TYPE_ROOT:
          case Statement.TYPE_SWITCH:
          case Statement.TYPE_SYNCRONIZED:
            stack.add(st.getFirst());
            break;
          default:
            return st;
        }
      }
    }

    return null;
  }

  private static Statement getDefStatement(Statement stat, VarType classType, Set<Statement> setStats) {
    List<Exprent> lst = new ArrayList<>();
    Statement retStat = null;

    if (stat.getExprents() == null) {
      int counter = 0;

      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement) {
          Statement st = (Statement)obj;

          Statement stTemp = getDefStatement(st, classType, setStats);

          if (stTemp != null) {
            if (counter == 1) {
              retStat = stat;
              break;
            }
            retStat = stTemp;
            counter++;
          }

          if (st.type == Statement.TYPE_DO) {
            DoStatement dost = (DoStatement)st;

            lst.addAll(dost.getInitExprentList());
            lst.addAll(dost.getConditionExprentList());
          }
        }
        else if (obj instanceof Exprent) {
          lst.add((Exprent)obj);
        }
      }
    }
    else {
      lst = stat.getExprents();
    }

    if (retStat != stat) {
      for (Exprent exprent : lst) {
        if (exprent != null && searchForClass(exprent, classType)) {
          retStat = stat;
          break;
        }
      }
    }

    if (retStat != null) {
      setStats.add(stat);
    }

    return retStat;
  }

  private static boolean searchForClass(Exprent exprent, VarType classType) {
    List<Exprent> lst = exprent.getAllExprents(true);
    lst.add(exprent);

    String classname = classType.value;

    for (Exprent expr : lst) {
      boolean res = false;

      switch (expr.type) {
        case Exprent.EXPRENT_CONST:
          ConstExprent constExpr = (ConstExprent)expr;
          res = (VarType.VARTYPE_CLASS.equals(constExpr.getConstType()) && classname.equals(constExpr.getValue()) ||
                 classType.equals(constExpr.getConstType()));
          break;
        case Exprent.EXPRENT_FIELD:
          res = classname.equals(((FieldExprent)expr).getClassname());
          break;
        case Exprent.EXPRENT_INVOCATION:
          res = classname.equals(((InvocationExprent)expr).getClassname());
          break;
        case Exprent.EXPRENT_NEW:
          VarType newType = expr.getExprType();
          res = newType.type == CodeConstants.TYPE_OBJECT && classname.equals(newType.value);
          break;
        case Exprent.EXPRENT_VAR:
          VarExprent varExpr = (VarExprent)expr;
          if (varExpr.isDefinition()) {
            VarType varType = varExpr.getVarType();
            if (classType.equals(varType) || (varType.arrayDim > 0 && classType.value.equals(varType.value))) {
              res = true;
            }
          }
      }

      if (res) {
        return true;
      }
    }

    return false;
  }

  private static class VarFieldPair {
    public String fieldKey = "";
    public VarVersionPair varPair;

    public VarFieldPair(String field, VarVersionPair varPair) {
      this.fieldKey = field;
      this.varPair = varPair;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (o == null || !(o instanceof VarFieldPair)) return false;

      VarFieldPair pair = (VarFieldPair)o;
      return fieldKey.equals(pair.fieldKey) && varPair.equals(pair.varPair);
    }

    @Override
    public int hashCode() {
      return fieldKey.hashCode() + varPair.hashCode();
    }
  }
}
