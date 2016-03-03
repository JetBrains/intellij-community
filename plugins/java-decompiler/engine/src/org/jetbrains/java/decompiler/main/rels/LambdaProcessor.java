/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructBootstrapMethodsAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.IOException;
import java.util.*;

public class LambdaProcessor {
  @SuppressWarnings("SpellCheckingInspection") private static final String JAVAC_LAMBDA_CLASS = "java/lang/invoke/LambdaMetafactory";
  @SuppressWarnings("SpellCheckingInspection") private static final String JAVAC_LAMBDA_METHOD = "metafactory";
  @SuppressWarnings("SpellCheckingInspection") private static final String JAVAC_LAMBDA_ALT_METHOD = "altMetafactory";

  public void processClass(ClassNode node) throws IOException {
    for (ClassNode child : node.nested) {
      processClass(child);
    }

    hasLambda(node);
  }

  public boolean hasLambda(ClassNode node) throws IOException {
    ClassesProcessor clProcessor = DecompilerContext.getClassProcessor();
    StructClass cl = node.classStruct;

    if (cl.getBytecodeVersion() < CodeConstants.BYTECODE_JAVA_8) { // lambda beginning with Java 8
      return false;
    }

    StructBootstrapMethodsAttribute bootstrap =
      (StructBootstrapMethodsAttribute)cl.getAttributes().getWithKey(StructGeneralAttribute.ATTRIBUTE_BOOTSTRAP_METHODS);
    if (bootstrap == null || bootstrap.getMethodsNumber() == 0) {
      return false; // no bootstrap constants in pool
    }

    BitSet lambda_methods = new BitSet();

    // find lambda bootstrap constants
    for (int i = 0; i < bootstrap.getMethodsNumber(); ++i) {
      LinkConstant method_ref = bootstrap.getMethodReference(i); // method handle

      // FIXME: extend for Eclipse etc. at some point
      if (JAVAC_LAMBDA_CLASS.equals(method_ref.classname) &&
          (JAVAC_LAMBDA_METHOD.equals(method_ref.elementname) || JAVAC_LAMBDA_ALT_METHOD.equals(method_ref.elementname))) {
        lambda_methods.set(i);
      }
    }

    if (lambda_methods.isEmpty()) {
      return false; // no lambda bootstrap constant found
    }

    Map<String, String> mapMethodsLambda = new HashMap<String, String>();

    // iterate over code and find invocations of bootstrap methods. Replace them with anonymous classes.
    for (StructMethod mt : cl.getMethods()) {
      mt.expandData();

      InstructionSequence seq = mt.getInstructionSequence();
      if (seq != null && seq.length() > 0) {
        int len = seq.length();

        for (int i = 0; i < len; ++i) {
          Instruction instr = seq.getInstr(i);

          if (instr.opcode == CodeConstants.opc_invokedynamic) {
            LinkConstant invoke_dynamic = cl.getPool().getLinkConstant(instr.getOperand(0));

            if (lambda_methods.get(invoke_dynamic.index1)) { // lambda invocation found

              List<PooledConstant> bootstrap_arguments = bootstrap.getMethodArguments(invoke_dynamic.index1);
              MethodDescriptor md = MethodDescriptor.parseDescriptor(invoke_dynamic.descriptor);

              String lambda_class_name = md.ret.value;
              String lambda_method_name = invoke_dynamic.elementname;
              String lambda_method_descriptor = ((PrimitiveConstant)bootstrap_arguments.get(2)).getString(); // method type

              LinkConstant content_method_handle = (LinkConstant)bootstrap_arguments.get(1);

              ClassNode node_lambda = new ClassNode(content_method_handle.classname, content_method_handle.elementname,
                                                    content_method_handle.descriptor, content_method_handle.index1,
                                                    lambda_class_name, lambda_method_name, lambda_method_descriptor, cl);
              node_lambda.simpleName = cl.qualifiedName + "##Lambda_" + invoke_dynamic.index1 + "_" + invoke_dynamic.index2;
              node_lambda.enclosingMethod = InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor());

              node.nested.add(node_lambda);
              node_lambda.parent = node;

              clProcessor.getMapRootClasses().put(node_lambda.simpleName, node_lambda);
              if (!node_lambda.lambdaInformation.is_method_reference) {
                mapMethodsLambda.put(node_lambda.lambdaInformation.content_method_key, node_lambda.simpleName);
              }
            }
          }
        }
      }

      mt.releaseResources();
    }

    // build class hierarchy on lambda
    for (ClassNode nd : node.nested) {
      if (nd.type == ClassNode.CLASS_LAMBDA) {
        String parent_class_name = mapMethodsLambda.get(nd.enclosingMethod);
        if (parent_class_name != null) {
          ClassNode parent_class = clProcessor.getMapRootClasses().get(parent_class_name);

          parent_class.nested.add(nd);
          nd.parent = parent_class;
        }
      }
    }

    // FIXME: mixed hierarchy?

    return false;
  }
}
