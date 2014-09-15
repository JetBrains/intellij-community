/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
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
    if (node.type == ClassNode.CLASS_LAMBDA && !node.lambda_information.is_method_reference) {
      ClassNode node_content = DecompilerContext.getClassProcessor().getMapRootClasses().get(node.classStruct.qualifiedName);
      if (node_content != null && node_content.wrapper != null) {
        node_content.wrapper.getHiddenMembers().add(node.lambda_information.content_method_key);
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
      // ensure not-empty class name
      if ((child.type == ClassNode.CLASS_LOCAL || child.type == ClassNode.CLASS_MEMBER) && child.simpleName == null) {
        StructClass cl = child.classStruct;
        if ((child.access & CodeConstants.ACC_SYNTHETIC) != 0 || cl.isSynthetic()) {
          child.simpleName = "SyntheticClass_" + (++synthetics);
        }
        else {
          DecompilerContext.getLogger().writeMessage("Nameless local or member class " + cl.qualifiedName + "!",
                                                     IFernflowerLogger.Severity.WARN);
          child.simpleName = "NamelessClass_" + (++nameless);
        }
      }
    }

    for (ClassNode child : node.nested) {
      if (child.type == ClassNode.CLASS_LAMBDA) {
        setLambdaVars(node, child);
      }
      else {
        if (child.type != ClassNode.CLASS_MEMBER || (child.access & CodeConstants.ACC_STATIC) == 0) {
          insertLocalVars(node, child);

          if (child.type == ClassNode.CLASS_LOCAL) {
            setLocalClassDefinition(node.wrapper.getMethods().getWithKey(child.enclosingMethod), child);
          }
        }
      }
    }

    for (ClassNode child : node.nested) {
      processClass(root, child);
    }
  }

  private static void setLambdaVars(ClassNode parent, ClassNode child) {

    if (child.lambda_information.is_method_reference) { // method reference, no code and no parameters
      return;
    }

    final MethodWrapper meth = parent.wrapper.getMethods().getWithKey(child.lambda_information.content_method_key);
    final MethodWrapper encmeth = parent.wrapper.getMethods().getWithKey(child.enclosingMethod);

    MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(child.lambda_information.method_descriptor);
    final MethodDescriptor md_content = MethodDescriptor.parseDescriptor(child.lambda_information.content_method_descriptor);

    final int vars_count = md_content.params.length - md_lambda.params.length;
    //		if(vars_count < 0) { // should not happen, but just in case...
    //			vars_count = 0;
    //		}

    final boolean is_static_lambda_content = child.lambda_information.is_content_method_static;

    final String parent_class_name = parent.wrapper.getClassStruct().qualifiedName;
    final String lambda_class_name = child.simpleName;

    final VarType lambda_class_type = new VarType(lambda_class_name, true);

    // this pointer
    if (!is_static_lambda_content && DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS)) {
      meth.varproc.getThisvars().put(new VarVersionPaar(0, 0), parent_class_name);
      meth.varproc.setVarName(new VarVersionPaar(0, 0), parent.simpleName + ".this");
    }

    // local variables
    DirectGraph graph = encmeth.getOrBuildGraph();

    final HashMap<VarVersionPaar, String> mapNewNames = new HashMap<VarVersionPaar, String>();

    graph.iterateExprents(new DirectGraph.ExprentIterator() {
      public int processExprent(Exprent exprent) {

        List<Exprent> lst = exprent.getAllExprents(true);
        lst.add(exprent);

        for (Exprent expr : lst) {

          if (expr.type == Exprent.EXPRENT_NEW) {
            NewExprent new_expr = (NewExprent)expr;
            if (new_expr.isLambda() && lambda_class_type.equals(new_expr.getNewtype())) {

              InvocationExprent inv_dynamic = new_expr.getConstructor();

              int param_index = is_static_lambda_content ? 0 : 1;
              int varindex = is_static_lambda_content ? 0 : 1;

              for (int i = 0; i < vars_count; ++i) {

                Exprent param = inv_dynamic.getLstParameters().get(param_index + i);

                if (param.type == Exprent.EXPRENT_VAR) {
                  VarVersionPaar enc_varpaar = new VarVersionPaar((VarExprent)param);
                  String enc_varname = encmeth.varproc.getVarName(enc_varpaar);

                  //meth.varproc.setVarName(new VarVersionPaar(varindex, 0), enc_varname);
                  mapNewNames.put(new VarVersionPaar(varindex, 0), enc_varname);
                }

                varindex += md_content.params[i].stack_size;
              }
            }
          }
        }

        return 0;
      }
    });

    // update names of local variables
    HashSet<String> setNewOuterNames = new HashSet<String>(mapNewNames.values());
    setNewOuterNames.removeAll(meth.setOuterVarNames);

    meth.varproc.refreshVarNames(new VarNamesCollector(setNewOuterNames));
    meth.setOuterVarNames.addAll(setNewOuterNames);

    for (Entry<VarVersionPaar, String> entr : mapNewNames.entrySet()) {
      meth.varproc.setVarName(entr.getKey(), entr.getValue());
    }
  }

  private static void checkNotFoundClasses(ClassNode root, ClassNode node) {

    List<ClassNode> lstChildren = new ArrayList<ClassNode>(node.nested);

    for (ClassNode child : lstChildren) {

      if ((child.type == ClassNode.CLASS_LOCAL || child.type == ClassNode.CLASS_ANONYMOUS) && child.enclosingMethod == null) {

        Set<String> setEnclosing = child.enclosingClasses;

        if (setEnclosing.size() == 1) {
          StructEnclosingMethodAttribute attr =
            (StructEnclosingMethodAttribute)child.classStruct.getAttributes().getWithKey("EnclosingMethod");
          if (attr != null && attr.getMethodName() != null) {
            if (node.classStruct.qualifiedName.equals(attr.getClassName()) &&
                node.classStruct.getMethod(attr.getMethodName(), attr.getMethodDescriptor()) != null) {
              child.enclosingMethod = InterpreterUtil.makeUniqueKey(attr.getMethodName(), attr.getMethodDescriptor());
              continue;
            }
          }
        }

        node.nested.remove(child);
        child.parent = null;
        setEnclosing.remove(node.classStruct.qualifiedName);

        boolean hasEnclosing = !setEnclosing.isEmpty();
        if (hasEnclosing) {
          hasEnclosing = insertNestedClass(root, child);
        }

        if (!hasEnclosing) {
          if (child.type == ClassNode.CLASS_ANONYMOUS) {
            DecompilerContext.getLogger()
              .writeMessage("Unreferenced anonymous class " + child.classStruct.qualifiedName + "!", IFernflowerLogger.Severity.WARN);
          }
          else if (child.type == ClassNode.CLASS_LOCAL) {
            DecompilerContext.getLogger()
              .writeMessage("Unreferenced local class " + child.classStruct.qualifiedName + "!", IFernflowerLogger.Severity.WARN);
          }
        }
      }
    }
  }

  private static boolean insertNestedClass(ClassNode root, ClassNode child) {

    Set<String> setEnclosing = child.enclosingClasses;

    LinkedList<ClassNode> stack = new LinkedList<ClassNode>();
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
    final HashMap<String, HashMap<String, List<VarFieldPair>>> mapVarMasks = new HashMap<String, HashMap<String, List<VarFieldPair>>>();

    int cltypes = 0;

    for (ClassNode nd : node.nested) {
      if (nd.type != ClassNode.CLASS_LAMBDA) {
        if ((nd.access & CodeConstants.ACC_STATIC) == 0 && (nd.access & CodeConstants.ACC_INTERFACE) == 0) {

          cltypes |= nd.type;

          HashMap<String, List<VarFieldPair>> mask = getMaskLocalVars(nd.wrapper);
          if (mask.isEmpty()) {
            DecompilerContext.getLogger()
              .writeMessage("Nested class " + nd.classStruct.qualifiedName + " has no constructor!", IFernflowerLogger.Severity.WARN);
          }
          else {
            mapVarMasks.put(nd.classStruct.qualifiedName, mask);
          }
        }
      }
    }

    // local var masks
    final HashMap<String, HashMap<String, List<VarFieldPair>>> mapVarFieldPairs =
      new HashMap<String, HashMap<String, List<VarFieldPair>>>();

    if (cltypes != ClassNode.CLASS_MEMBER) {

      // iterate enclosing class
      for (final MethodWrapper meth : node.wrapper.getMethods()) {

        if (meth.root != null) { // neither abstract, nor native
          DirectGraph graph = meth.getOrBuildGraph();

          graph.iterateExprents(new DirectGraph.ExprentIterator() {
            public int processExprent(Exprent exprent) {
              List<Exprent> lst = exprent.getAllExprents(true);
              lst.add(exprent);

              for (Exprent expr : lst) {

                if (expr.type == Exprent.EXPRENT_NEW) {
                  InvocationExprent constr = ((NewExprent)expr).getConstructor();

                  if (constr != null && mapVarMasks.containsKey(constr.getClassname())) { // non-static inner class constructor

                    String refclname = constr.getClassname();

                    ClassNode nestedClassNode = node.getClassNode(refclname);

                    if (nestedClassNode.type != ClassNode.CLASS_MEMBER) {

                      List<VarFieldPair> mask = mapVarMasks.get(refclname).get(constr.getStringDescriptor());

                      if (!mapVarFieldPairs.containsKey(refclname)) {
                        mapVarFieldPairs.put(refclname, new HashMap<String, List<VarFieldPair>>());
                      }

                      List<VarFieldPair> lstTemp = new ArrayList<VarFieldPair>();

                      for (int i = 0; i < mask.size(); i++) {
                        Exprent param = constr.getLstParameters().get(i);
                        VarFieldPair pair = null;

                        if (param.type == Exprent.EXPRENT_VAR && mask.get(i) != null) {
                          VarVersionPaar varpaar = new VarVersionPaar((VarExprent)param);

                          // FIXME: final flags of variables are wrong! Correct the entire final functionality.
                          //													if(meth.varproc.getVarFinal(varpaar) != VarTypeProcessor.VAR_NONFINAL) {
                          pair = new VarFieldPair(mask.get(i).keyfield, varpaar);
                          //													}
                        }

                        lstTemp.add(pair);
                      }

                      List<VarFieldPair> pairmask = mapVarFieldPairs.get(refclname).get(constr.getStringDescriptor());

                      if (pairmask == null) {
                        pairmask = lstTemp;
                      }
                      else {
                        for (int i = 0; i < pairmask.size(); i++) {
                          if (!InterpreterUtil.equalObjects(pairmask.get(i), lstTemp.get(i))) {
                            pairmask.set(i, null);
                          }
                        }
                      }

                      mapVarFieldPairs.get(refclname).put(constr.getStringDescriptor(), pairmask);
                      nestedClassNode.enclosingMethod =
                        InterpreterUtil.makeUniqueKey(meth.methodStruct.getName(), meth.methodStruct.getDescriptor());
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
    for (Entry<String, HashMap<String, List<VarFieldPair>>> entcl : mapVarMasks.entrySet()) {

      ClassNode nestedNode = node.getClassNode(entcl.getKey());

      // intersection
      List<VarFieldPair> intrPairMask = null;
      // merge referenced constructors
      if (mapVarFieldPairs.containsKey(entcl.getKey())) {
        for (List<VarFieldPair> mask : mapVarFieldPairs.get(entcl.getKey()).values()) {
          if (intrPairMask == null) {
            intrPairMask = new ArrayList<VarFieldPair>(mask);
          }
          else {
            mergeListSignatures(intrPairMask, mask, false);
          }
        }
      }

      List<VarFieldPair> intrMask = null;
      // merge all constructors
      for (List<VarFieldPair> mask : entcl.getValue().values()) {
        if (intrMask == null) {
          intrMask = new ArrayList<VarFieldPair>(mask);
        }
        else {
          mergeListSignatures(intrMask, mask, false);
        }
      }

      if (intrPairMask == null) { // member or local and never instantiated
        intrPairMask = new ArrayList<VarFieldPair>(intrMask);

        boolean found = false;

        for (int i = 0; i < intrPairMask.size(); i++) {
          if (intrPairMask.get(i) != null) {
            if (found) {
              intrPairMask.set(i, null);
            }
            found = true;
          }
        }
      }

      mergeListSignatures(intrPairMask, intrMask, true);

      for (int i = 0; i < intrPairMask.size(); i++) {
        VarFieldPair pair = intrPairMask.get(i);
        if (pair != null && pair.keyfield.length() > 0) {
          nestedNode.mapFieldsToVars.put(pair.keyfield, pair.varpaar);
        }
      }

      // set resulting constructor signatures
      for (Entry<String, List<VarFieldPair>> entmt : entcl.getValue().entrySet()) {
        mergeListSignatures(entmt.getValue(), intrPairMask, false);

        MethodWrapper meth = nestedNode.wrapper.getMethodWrapper("<init>", entmt.getKey());
        meth.signatureFields = new ArrayList<VarVersionPaar>();

        for (VarFieldPair pair : entmt.getValue()) {
          meth.signatureFields.add(pair == null ? null : pair.varpaar);
        }
      }
    }
  }

  private static void insertLocalVars(final ClassNode parent, final ClassNode child) {

    // enclosing method, is null iff member class
    MethodWrapper encmeth = parent.wrapper.getMethods().getWithKey(child.enclosingMethod);

    // iterate all child methods
    for (final MethodWrapper meth : child.wrapper.getMethods()) {

      if (meth.root != null) { // neither abstract nor native

        // local var names
        HashMap<VarVersionPaar, String> mapNewNames = new HashMap<VarVersionPaar, String>();
        // local var types
        HashMap<VarVersionPaar, VarType> mapNewTypes = new HashMap<VarVersionPaar, VarType>();

        final HashMap<Integer, VarVersionPaar> mapParamsToNewVars = new HashMap<Integer, VarVersionPaar>();
        if (meth.signatureFields != null) {
          int index = 0;
          int varindex = 1;
          MethodDescriptor md = MethodDescriptor.parseDescriptor(meth.methodStruct.getDescriptor());

          for (VarVersionPaar paar : meth.signatureFields) {
            if (paar != null) {
              VarVersionPaar newvar = new VarVersionPaar(meth.counter.getCounterAndIncrement(CounterContainer.VAR_COUNTER), 0);

              mapParamsToNewVars.put(varindex, newvar);

              String varname = null;
              VarType vartype = null;

              if (child.type != ClassNode.CLASS_MEMBER) {
                varname = encmeth.varproc.getVarName(paar);
                vartype = encmeth.varproc.getVarType(paar);

                encmeth.varproc.setVarFinal(paar, VarTypeProcessor.VAR_FINALEXPLICIT);
              }

              if (paar.var == -1 || "this".equals(varname)) {
                if (parent.simpleName == null) {
                  // anonymous enclosing class, no access to this
                  varname = VarExprent.VAR_NAMELESS_ENCLOSURE;
                }
                else {
                  varname = parent.simpleName + ".this";
                }
                meth.varproc.getThisvars().put(newvar, parent.classStruct.qualifiedName);
              }

              mapNewNames.put(newvar, varname);
              mapNewTypes.put(newvar, vartype);
            }
            varindex += md.params[index++].stack_size;
          }
        }

        // new vars
        final HashMap<String, VarVersionPaar> mapFieldsToNewVars = new HashMap<String, VarVersionPaar>();

        for (ClassNode clnode = child; clnode != null; clnode = clnode.parent) {

          for (Entry<String, VarVersionPaar> entr : clnode.mapFieldsToVars.entrySet()) {
            VarVersionPaar newvar = new VarVersionPaar(meth.counter.getCounterAndIncrement(CounterContainer.VAR_COUNTER), 0);

            mapFieldsToNewVars.put(InterpreterUtil.makeUniqueKey(clnode.classStruct.qualifiedName, entr.getKey()), newvar);

            String varname = null;
            VarType vartype = null;

            if (clnode.type != ClassNode.CLASS_MEMBER) {

              MethodWrapper enclosing_method = clnode.parent.wrapper.getMethods().getWithKey(clnode.enclosingMethod);

              varname = enclosing_method.varproc.getVarName(entr.getValue());
              vartype = enclosing_method.varproc.getVarType(entr.getValue());

              enclosing_method.varproc.setVarFinal(entr.getValue(), VarTypeProcessor.VAR_FINALEXPLICIT);
            }

            if (entr.getValue().var == -1 || "this".equals(varname)) {
              if (clnode.parent.simpleName == null) {
                // anonymous enclosing class, no access to this
                varname = VarExprent.VAR_NAMELESS_ENCLOSURE;
              }
              else {
                varname = clnode.parent.simpleName + ".this";
              }
              meth.varproc.getThisvars().put(newvar, clnode.parent.classStruct.qualifiedName);
            }

            mapNewNames.put(newvar, varname);
            mapNewTypes.put(newvar, vartype);

            // hide synthetic field
            if (clnode == child) { // fields higher up the chain were already handled with their classes
              StructField fd = child.classStruct.getFields().getWithKey(entr.getKey());
              child.wrapper.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
            }
          }
        }

        HashSet<String> setNewOuterNames = new HashSet<String>(mapNewNames.values());
        setNewOuterNames.removeAll(meth.setOuterVarNames);

        meth.varproc.refreshVarNames(new VarNamesCollector(setNewOuterNames));
        meth.setOuterVarNames.addAll(setNewOuterNames);

        for (Entry<VarVersionPaar, String> entr : mapNewNames.entrySet()) {
          VarVersionPaar varpaar = entr.getKey();
          VarType vartype = mapNewTypes.get(varpaar);

          meth.varproc.setVarName(varpaar, entr.getValue());
          if (vartype != null) {
            meth.varproc.setVarType(varpaar, vartype);
          }
        }

        DirectGraph graph = meth.getOrBuildGraph();

        graph.iterateExprents(new DirectGraph.ExprentIterator() {
          public int processExprent(Exprent exprent) {

            if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
              AssignmentExprent asexpr = (AssignmentExprent)exprent;
              if (asexpr.getLeft().type == Exprent.EXPRENT_FIELD) {
                FieldExprent fexpr = (FieldExprent)asexpr.getLeft();

                if (fexpr.getClassname().equals(child.classStruct.qualifiedName) &&  // process this class only
                    mapFieldsToNewVars.containsKey(InterpreterUtil.makeUniqueKey(child.classStruct.qualifiedName,
                                                                                 InterpreterUtil.makeUniqueKey(fexpr.getName(), fexpr
                                                                                   .getDescriptor().descriptorString)))) {
                  return 2;
                }

                //if(fexpr.getClassname().equals(child.classStruct.qualifiedName) &&
                //		mapFieldsToNewVars.containsKey(InterpreterUtil.makeUniqueKey(fexpr.getName(), fexpr.getDescriptor().descriptorString))) {
                //	return 2;
                //}
              }
            }

            if (child.type == ClassNode.CLASS_ANONYMOUS && "<init>".equals(meth.methodStruct.getName())
                && exprent.type == Exprent.EXPRENT_INVOCATION) {
              InvocationExprent invexpr = (InvocationExprent)exprent;
              if (invexpr.getFunctype() == InvocationExprent.TYP_INIT) {
                // invocation of the super constructor in an anonymous class
                child.superInvocation = invexpr; // FIXME: save original names of parameters
                return 2;
              }
            }

            replaceExprent(exprent);

            return 0;
          }

          private Exprent replaceExprent(Exprent exprent) {

            if (exprent.type == Exprent.EXPRENT_VAR) {
              int varindex = ((VarExprent)exprent).getIndex();
              if (mapParamsToNewVars.containsKey(varindex)) {
                VarVersionPaar newvar = mapParamsToNewVars.get(varindex);
                meth.varproc.getExternvars().add(newvar);
                return new VarExprent(newvar.var, meth.varproc.getVarType(newvar), meth.varproc);
              }
            }
            else if (exprent.type == Exprent.EXPRENT_FIELD) {
              FieldExprent fexpr = (FieldExprent)exprent;

              String keyField = InterpreterUtil.makeUniqueKey(fexpr.getClassname(), InterpreterUtil
                .makeUniqueKey(fexpr.getName(), fexpr.getDescriptor().descriptorString));

              if (mapFieldsToNewVars.containsKey(keyField)) {
                //if(fexpr.getClassname().equals(child.classStruct.qualifiedName) &&
                //		mapFieldsToNewVars.containsKey(keyField)) {
                VarVersionPaar newvar = mapFieldsToNewVars.get(keyField);
                meth.varproc.getExternvars().add(newvar);
                return new VarExprent(newvar.var, meth.varproc.getVarType(newvar), meth.varproc);
              }
            }

            boolean replaced = true;
            while (replaced) {
              replaced = false;

              for (Exprent expr : exprent.getAllExprents()) {
                Exprent retexpr = replaceExprent(expr);
                if (retexpr != null) {
                  exprent.replaceExprent(expr, retexpr);
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

  private static HashMap<String, List<VarFieldPair>> getMaskLocalVars(ClassWrapper wrapper) {

    HashMap<String, List<VarFieldPair>> mapMasks = new HashMap<String, List<VarFieldPair>>();

    StructClass cl = wrapper.getClassStruct();

    // iterate over constructors
    for (StructMethod mt : cl.getMethods()) {
      if ("<init>".equals(mt.getName())) {

        MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

        MethodWrapper meth = wrapper.getMethodWrapper("<init>", mt.getDescriptor());
        DirectGraph graph = meth.getOrBuildGraph();

        if (graph != null) { // something gone wrong, should not be null
          List<VarFieldPair> fields = new ArrayList<VarFieldPair>();

          int varindex = 1;
          for (int i = 0; i < md.params.length; i++) {  // no static methods allowed
            String keyField = getEnclosingVarField(cl, meth, graph, varindex);
            fields.add(keyField == null ? null : new VarFieldPair(keyField, new VarVersionPaar(-1, 0))); // TODO: null?
            varindex += md.params[i].stack_size;
          }
          mapMasks.put(mt.getDescriptor(), fields);
        }
      }
    }

    return mapMasks;
  }

  private static String getEnclosingVarField(StructClass cl, MethodWrapper meth, DirectGraph graph, final int index) {

    String field = "";

    // parameter variable final
    if (meth.varproc.getVarFinal(new VarVersionPaar(index, 0)) == VarTypeProcessor.VAR_NONFINAL) {
      return null;
    }

    boolean noSynthFlag = DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET);

    // no loop at the begin
    DirectNode firstnode = graph.first;
    if (firstnode.preds.isEmpty()) {
      // assignment to a final synthetic field?
      for (Exprent exprent : firstnode.exprents) {
        if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
          AssignmentExprent asexpr = (AssignmentExprent)exprent;
          if (asexpr.getRight().type == Exprent.EXPRENT_VAR && ((VarExprent)asexpr.getRight()).getIndex() == index) {
            if (asexpr.getLeft().type == Exprent.EXPRENT_FIELD) {

              FieldExprent left = (FieldExprent)asexpr.getLeft();
              StructField fd = cl.getField(left.getName(), left.getDescriptor().descriptorString);

              if (fd != null) { // local (== not inherited) field
                if (cl.qualifiedName.equals(left.getClassname()) &&
                    fd.hasModifier(CodeConstants.ACC_FINAL) &&
                    (fd.isSynthetic() || (noSynthFlag && fd.hasModifier(CodeConstants.ACC_PRIVATE)))) {
                  field = InterpreterUtil.makeUniqueKey(left.getName(), left.getDescriptor().descriptorString);
                  break;
                }
              }
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

      VarFieldPair fobj = first.get(first.size() - i);
      VarFieldPair sobj = second.get(second.size() - i);

      boolean eq = false;
      if (fobj == null || sobj == null) {
        eq = (fobj == sobj);
      }
      else {
        eq = true;
        if (fobj.keyfield.length() == 0) {
          fobj.keyfield = sobj.keyfield;
        }
        else if (sobj.keyfield.length() == 0) {
          if (both) {
            sobj.keyfield = fobj.keyfield;
          }
        }
        else {
          eq = fobj.keyfield.equals(sobj.keyfield);
        }
      }

      if (!eq) {
        first.set(first.size() - i, null);
        if (both) {
          second.set(second.size() - i, null);
        }
      }
      else {
        if (fobj != null) {
          if (fobj.varpaar.var == -1) {
            fobj.varpaar = sobj.varpaar;
          }
          else {
            sobj.varpaar = fobj.varpaar;
          }
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
      VarFieldPair fobj = first.get(0);
      VarFieldPair sobj = second.get(0);

      boolean eq = false;
      if (fobj == null || sobj == null) {
        eq = (fobj == sobj);
      }
      else {
        eq = true;
        if (fobj.keyfield.length() == 0) {
          fobj.keyfield = sobj.keyfield;
        }
        else if (sobj.keyfield.length() == 0) {
          if (both) {
            sobj.keyfield = fobj.keyfield;
          }
        }
        else {
          eq = fobj.keyfield.equals(sobj.keyfield);
        }
      }

      if (!eq) {
        first.set(0, null);
        if (both) {
          second.set(0, null);
        }
      }
      else if (fobj != null) {
        if (fobj.varpaar.var == -1) {
          fobj.varpaar = sobj.varpaar;
        }
        else {
          sobj.varpaar = fobj.varpaar;
        }
      }
    }
  }


  private static void setLocalClassDefinition(MethodWrapper meth, ClassNode node) {

    RootStatement root = meth.root;

    HashSet<Statement> setStats = new HashSet<Statement>();
    VarType classtype = new VarType(node.classStruct.qualifiedName, true);

    Statement stdef = getDefStatement(root, classtype, setStats);
    if (stdef == null) {
      // unreferenced local class
      stdef = root.getFirst();
    }

    Statement first = findFirstBlock(stdef, setStats);

    List<Exprent> lst;
    if (first == null) {
      lst = stdef.getVarDefinitions();
    }
    else if (first.getExprents() == null) {
      lst = first.getVarDefinitions();
    }
    else {
      lst = first.getExprents();
    }


    int addindex = 0;
    for (Exprent expr : lst) {
      if (searchForClass(expr, classtype)) {
        break;
      }
      addindex++;
    }

    VarExprent var = new VarExprent(meth.counter.getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                                    classtype, meth.varproc);
    var.setDefinition(true);
    var.setClassdef(true);

    lst.add(addindex, var);
  }


  private static Statement findFirstBlock(Statement stat, HashSet<Statement> setStats) {

    LinkedList<Statement> stack = new LinkedList<Statement>();
    stack.add(stat);

    while (!stack.isEmpty()) {
      Statement st = stack.remove(0);

      if (stack.isEmpty() || setStats.contains(st)) {

        if (st.isLabeled() && !stack.isEmpty()) {
          return st;
        }

        if (st.getExprents() != null) {
          return st;
        }
        else {
          stack.clear();

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
    }

    return null;
  }


  private static Statement getDefStatement(Statement stat, VarType classtype, HashSet<Statement> setStats) {

    List<Exprent> condlst = new ArrayList<Exprent>();
    Statement retstat = null;

    if (stat.getExprents() == null) {
      int counter = 0;

      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement) {
          Statement st = (Statement)obj;

          Statement stTemp = getDefStatement(st, classtype, setStats);

          if (stTemp != null) {
            if (counter == 1) {
              retstat = stat;
              break;
            }
            retstat = stTemp;
            counter++;
          }

          if (st.type == DoStatement.TYPE_DO) {
            DoStatement dost = (DoStatement)st;

            condlst.addAll(dost.getInitExprentList());
            condlst.addAll(dost.getConditionExprentList());
          }
        }
        else if (obj instanceof Exprent) {
          condlst.add((Exprent)obj);
        }
      }
    }
    else {
      condlst = stat.getExprents();
    }

    if (retstat != stat) {
      for (Exprent exprent : condlst) {
        if (exprent != null && searchForClass(exprent, classtype)) {
          retstat = stat;
          break;
        }
      }
    }

    if (retstat != null) {
      setStats.add(stat);
    }

    return retstat;
  }

  private static boolean searchForClass(Exprent exprent, VarType classtype) {

    List<Exprent> lst = exprent.getAllExprents(true);
    lst.add(exprent);

    String classname = classtype.value;

    for (Exprent expr : lst) {

      boolean res = false;

      switch (expr.type) {
        case Exprent.EXPRENT_CONST:
          ConstExprent cexpr = (ConstExprent)expr;
          res = (VarType.VARTYPE_CLASS.equals(cexpr.getConsttype()) && classname.equals(cexpr.getValue()) ||
                 classtype.equals(cexpr.getConsttype()));
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
          VarExprent vexpr = (VarExprent)expr;
          if (vexpr.isDefinition()) {
            VarType vtype = vexpr.getVartype();
            if (classtype.equals(vtype) || (vtype.arraydim > 0 && classtype.value.equals(vtype.value))) {
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

    public String keyfield = "";
    public VarVersionPaar varpaar;

    public VarFieldPair(String field, VarVersionPaar varpaar) {
      this.keyfield = field;
      this.varpaar = varpaar;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (o == null || !(o instanceof VarFieldPair)) return false;

      VarFieldPair pair = (VarFieldPair)o;
      return keyfield.equals(pair.keyfield) && varpaar.equals(pair.varpaar);
    }

    @Override
    public int hashCode() {
      return keyfield.hashCode() + varpaar.hashCode();
    }
  }
}
