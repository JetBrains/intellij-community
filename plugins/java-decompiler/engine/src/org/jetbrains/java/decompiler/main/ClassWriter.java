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
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarTypeProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMember;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.*;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.*;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassWriter {

  private ClassReference14Processor ref14processor;
  private PoolInterceptor interceptor;

  public ClassWriter() {
    ref14processor = new ClassReference14Processor();
    interceptor = DecompilerContext.getPoolInterceptor();
  }

  private void invokeProcessors(ClassNode node) {
    ClassWrapper wrapper = node.wrapper;
    StructClass cl = wrapper.getClassStruct();

    InitializerProcessor.extractInitializers(wrapper);

    if (node.type == ClassNode.CLASS_ROOT && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_CLASS_1_4)) {
      ref14processor.processClassReferences(node);
    }

    if (cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM)) {
      EnumProcessor.clearEnum(wrapper);
    }

    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ASSERTIONS)) {
      AssertProcessor.buildAssertions(node);
    }
  }

  public void classLambdaToJava(ClassNode node, StringBuilder buffer, Exprent method_object, int indent) {
    // get the class node with the content method
    ClassNode classNode = node;
    while (classNode != null && classNode.type == ClassNode.CLASS_LAMBDA) {
      classNode = classNode.parent;
    }
    if (classNode == null) {
      return;
    }

    boolean lambdaToAnonymous = DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS);

    ClassNode outerNode = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

    try {
      ClassWrapper wrapper = classNode.wrapper;
      StructClass cl = wrapper.getClassStruct();

      DecompilerContext.getLogger().startWriteClass(node.simpleName);

      if (node.lambda_information.is_method_reference) {
        if (!node.lambda_information.is_content_method_static && method_object != null) {
          // reference to a virtual method
          buffer.append(method_object.toJava(indent));
        }
        else {
          // reference to a static method
          buffer.append(ExprProcessor.getCastTypeName(new VarType(node.lambda_information.content_class_name, false)));
        }

        buffer.append("::");
        buffer.append(node.lambda_information.content_method_name);
      }
      else {
        // lambda method
        StructMethod mt = cl.getMethod(node.lambda_information.content_method_key);
        MethodWrapper methodWrapper = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());
        MethodDescriptor md_content = MethodDescriptor.parseDescriptor(node.lambda_information.content_method_descriptor);
        MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(node.lambda_information.method_descriptor);

        if (!lambdaToAnonymous) {
          buffer.append('(');

          boolean firstParameter = true;
          int index = node.lambda_information.is_content_method_static ? 0 : 1;
          int start_index = md_content.params.length - md_lambda.params.length;

          for (int i = 0; i < md_content.params.length; i++) {
            if (i >= start_index) {
              if (!firstParameter) {
                buffer.append(", ");
              }

              String parameterName = methodWrapper.varproc.getVarName(new VarVersionPaar(index, 0));
              buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

              firstParameter = false;
            }

            index += md_content.params[i].stack_size;
          }

          buffer.append(") ->");
        }

        buffer.append(" {");
        buffer.append(DecompilerContext.getNewLineSeparator());

        methodLambdaToJava(node, classNode, mt, buffer, indent + 1, !lambdaToAnonymous);

        InterpreterUtil.appendIndent(buffer, indent);
        buffer.append("}");
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, outerNode);
    }

    DecompilerContext.getLogger().endWriteClass();
  }

  public void classToJava(ClassNode node, StringBuilder buffer, int indent) {
    ClassNode outerNode = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

    try {
      // last minute processing
      invokeProcessors(node);

      ClassWrapper wrapper = node.wrapper;
      StructClass cl = wrapper.getClassStruct();

      DecompilerContext.getLogger().startWriteClass(cl.qualifiedName);

      String lineSeparator = DecompilerContext.getNewLineSeparator();

      writeClassDefinition(node, buffer, indent);

      boolean hasContent = false;

      // fields
      boolean enumFields = false;

      for (StructField fd : cl.getFields()) {
        boolean hide = fd.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
                       wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
        if (hide) continue;

        boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
        if (isEnum) {
          if (enumFields) {
            buffer.append(',');
            buffer.append(lineSeparator);
          }
          enumFields = true;
        }
        else if (enumFields) {
          buffer.append(';');
          buffer.append(lineSeparator);
          buffer.append(lineSeparator);
          enumFields = false;
        }

        fieldToJava(wrapper, cl, fd, buffer, indent + 1);

        hasContent = true;
      }

      if (enumFields) {
        buffer.append(';');
        buffer.append(lineSeparator);
      }

      // methods
      for (StructMethod mt : cl.getMethods()) {
        boolean hide = mt.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
                       mt.hasModifier(CodeConstants.ACC_BRIDGE) && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_BRIDGE) ||
                       wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
        if (hide) continue;

        int position = buffer.length();
        if (hasContent) {
          buffer.append(lineSeparator);
        }
        boolean methodSkipped = !methodToJava(node, mt, buffer, indent + 1);
        if (!methodSkipped) {
          hasContent = true;
        }
        else {
          buffer.setLength(position);
        }
      }

      // member classes
      for (ClassNode inner : node.nested) {
        if (inner.type == ClassNode.CLASS_MEMBER) {
          StructClass innerCl = inner.classStruct;
          boolean isSynthetic = (inner.access & CodeConstants.ACC_SYNTHETIC) != 0 || innerCl.isSynthetic();
          boolean hide = isSynthetic && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
                         wrapper.getHiddenMembers().contains(innerCl.qualifiedName);
          if (hide) continue;

          if (hasContent) {
            buffer.append(lineSeparator);
          }
          classToJava(inner, buffer, indent + 1);

          hasContent = true;
        }
      }

      InterpreterUtil.appendIndent(buffer, indent);
      buffer.append('}');

      if (node.type != ClassNode.CLASS_ANONYMOUS) {
        buffer.append(lineSeparator);
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, outerNode);
    }

    DecompilerContext.getLogger().endWriteClass();
  }

  private void writeClassDefinition(ClassNode node, StringBuilder buffer, int indent) {
    String lineSeparator = DecompilerContext.getNewLineSeparator();
    String indentString = InterpreterUtil.getIndentString(indent);

    if (node.type == ClassNode.CLASS_ANONYMOUS) {
      buffer.append(" {");
      buffer.append(lineSeparator);
      return;
    }

    ClassWrapper wrapper = node.wrapper;
    StructClass cl = wrapper.getClassStruct();

    int flags = node.type == ClassNode.CLASS_ROOT ? cl.getAccessFlags() : node.access;
    boolean isDeprecated = cl.getAttributes().containsKey("Deprecated");
    boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || cl.getAttributes().containsKey("Synthetic");
    boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;
    boolean isInterface = (flags & CodeConstants.ACC_INTERFACE) != 0;
    boolean isAnnotation = (flags & CodeConstants.ACC_ANNOTATION) != 0;

    if (isDeprecated) {
      appendDeprecation(buffer, indentString, lineSeparator);
    }

    if (interceptor != null) {
      String oldName = interceptor.getOldName(cl.qualifiedName);
      appendRenameComment(buffer, oldName, MType.CLASS, indent, lineSeparator);
    }

    if (isSynthetic) {
      appendComment(buffer, "synthetic class", indentString, lineSeparator);
    }

    appendAnnotations(buffer, cl, indent, lineSeparator);

    buffer.append(indentString);

    if (isEnum) {
      // remove abstract and final flags (JLS 8.9 Enums)
      flags &= ~CodeConstants.ACC_ABSTRACT;
      flags &= ~CodeConstants.ACC_FINAL;
    }

    appendModifiers(buffer, flags, CLASS_ALLOWED, isInterface, CLASS_EXCLUDED);

    if (isEnum) {
      buffer.append("enum ");
    }
    else if (isInterface) {
      if (isAnnotation) {
        buffer.append('@');
      }
      buffer.append("interface ");
    }
    else {
      buffer.append("class ");
    }

    GenericClassDescriptor descriptor = null;
    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
      StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)cl.getAttributes().getWithKey("Signature");
      if (attr != null) {
        descriptor = GenericMain.parseClassSignature(attr.getSignature());
      }
    }

    buffer.append(node.simpleName);

    if (descriptor != null && !descriptor.fparameters.isEmpty()) {
      appendTypeParameters(buffer, descriptor.fparameters, descriptor.fbounds);
    }

    buffer.append(' ');

    if (!isEnum && !isInterface && cl.superClass != null) {
      VarType supertype = new VarType(cl.superClass.getString(), true);
      if (!VarType.VARTYPE_OBJECT.equals(supertype)) {
        buffer.append("extends ");
        if (descriptor != null) {
          buffer.append(GenericMain.getGenericCastTypeName(descriptor.superclass));
        }
        else {
          buffer.append(ExprProcessor.getCastTypeName(supertype));
        }
        buffer.append(' ');
      }
    }

    if (!isAnnotation) {
      int[] interfaces = cl.getInterfaces();
      if (interfaces.length > 0) {
        buffer.append(isInterface ? "extends " : "implements ");
        for (int i = 0; i < interfaces.length; i++) {
          if (i > 0) {
            buffer.append(", ");
          }
          if (descriptor != null) {
            buffer.append(GenericMain.getGenericCastTypeName(descriptor.superinterfaces.get(i)));
          }
          else {
            buffer.append(ExprProcessor.getCastTypeName(new VarType(cl.getInterface(i), true)));
          }
        }
        buffer.append(' ');
      }
    }

    buffer.append('{');
    buffer.append(lineSeparator);
  }

  private void fieldToJava(ClassWrapper wrapper, StructClass cl, StructField fd, StringBuilder buffer, int indent) {
    String indentString = InterpreterUtil.getIndentString(indent);
    String lineSeparator = DecompilerContext.getNewLineSeparator();

    boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
    boolean isDeprecated = fd.getAttributes().containsKey("Deprecated");
    boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);

    if (isDeprecated) {
      appendDeprecation(buffer, indentString, lineSeparator);
    }

    if (interceptor != null) {
      String oldName = interceptor.getOldName(cl.qualifiedName + " " + fd.getName() + " " + fd.getDescriptor());
      appendRenameComment(buffer, oldName, MType.FIELD, indent, lineSeparator);
    }

    if (fd.isSynthetic()) {
      appendComment(buffer, "synthetic field", indentString, lineSeparator);
    }

    appendAnnotations(buffer, fd, indent, lineSeparator);

    buffer.append(indentString);

    if (!isEnum) {
      appendModifiers(buffer, fd.getAccessFlags(), FIELD_ALLOWED, isInterface, FIELD_EXCLUDED);
    }

    VarType fieldType = new VarType(fd.getDescriptor(), false);

    GenericFieldDescriptor descriptor = null;
    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
      StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)fd.getAttributes().getWithKey("Signature");
      if (attr != null) {
        descriptor = GenericMain.parseFieldSignature(attr.getSignature());
      }
    }

    if (!isEnum) {
      if (descriptor != null) {
        buffer.append(GenericMain.getGenericCastTypeName(descriptor.type));
      }
      else {
        buffer.append(ExprProcessor.getCastTypeName(fieldType));
      }
      buffer.append(' ');
    }

    buffer.append(fd.getName());

    Exprent initializer;
    if (fd.hasModifier(CodeConstants.ACC_STATIC)) {
      initializer = wrapper.getStaticFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    }
    else {
      initializer = wrapper.getDynamicFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    }
    if (initializer != null) {
      if (isEnum && initializer.type == Exprent.EXPRENT_NEW) {
        NewExprent nexpr = (NewExprent)initializer;
        nexpr.setEnumconst(true);
        buffer.append(nexpr.toJava(indent));
      }
      else {
        buffer.append(" = ");
        buffer.append(initializer.toJava(indent));
      }
    }
    else if (fd.hasModifier(CodeConstants.ACC_FINAL) && fd.hasModifier(CodeConstants.ACC_STATIC)) {
      StructConstantValueAttribute attr =
        (StructConstantValueAttribute)fd.getAttributes().getWithKey(StructGeneralAttribute.ATTRIBUTE_CONSTANT_VALUE);
      if (attr != null) {
        PrimitiveConstant constant = cl.getPool().getPrimitiveConstant(attr.getIndex());
        buffer.append(" = ");
        buffer.append(new ConstExprent(fieldType, constant.value).toJava(indent));
      }
    }

    if (!isEnum) {
      buffer.append(";");
      buffer.append(lineSeparator);
    }
  }

  private static void methodLambdaToJava(ClassNode lambdaNode,
                                         ClassNode classNode,
                                         StructMethod mt,
                                         StringBuilder buffer,
                                         int indent,
                                         boolean codeOnly) {
    ClassWrapper classWrapper = classNode.wrapper;
    MethodWrapper methodWrapper = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

    MethodWrapper outerWrapper = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

    try {
      String method_name = lambdaNode.lambda_information.method_name;
      MethodDescriptor md_content = MethodDescriptor.parseDescriptor(lambdaNode.lambda_information.content_method_descriptor);
      MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(lambdaNode.lambda_information.method_descriptor);

      if (!codeOnly) {
        InterpreterUtil.appendIndent(buffer, indent);
        buffer.append("public ");
        buffer.append(method_name);
        buffer.append("(");

        boolean firstParameter = true;
        int index = lambdaNode.lambda_information.is_content_method_static ? 0 : 1;
        int start_index = md_content.params.length - md_lambda.params.length;

        for (int i = 0; i < md_content.params.length; i++) {
          if (i >= start_index) {
            if (!firstParameter) {
              buffer.append(", ");
            }

            String typeName = ExprProcessor.getCastTypeName(md_content.params[i].copy());
            if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName) &&
                DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
              typeName = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
            }

            buffer.append(typeName);
            buffer.append(" ");

            String parameterName = methodWrapper.varproc.getVarName(new VarVersionPaar(index, 0));
            buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

            firstParameter = false;
          }

          index += md_content.params[i].stack_size;
        }

        buffer.append(") {");
        buffer.append(DecompilerContext.getNewLineSeparator());

        indent += 1;
      }

      if (!methodWrapper.decompiledWithErrors) {
        RootStatement root = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;
        if (root != null) { // check for existence
          try {
            buffer.append(root.toJava(indent));
          }
          catch (Throwable ex) {
            DecompilerContext.getLogger().writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " couldn't be written.", ex);
            methodWrapper.decompiledWithErrors = true;
          }
        }
      }

      if (methodWrapper.decompiledWithErrors) {
        InterpreterUtil.appendIndent(buffer, indent);
        buffer.append("// $FF: Couldn't be decompiled");
        buffer.append(DecompilerContext.getNewLineSeparator());
      }

      if (!codeOnly) {
        indent -= 1;

        InterpreterUtil.appendIndent(buffer, indent);
        buffer.append('}');
        buffer.append(DecompilerContext.getNewLineSeparator());
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
    }
  }

  private boolean methodToJava(ClassNode node, StructMethod mt, StringBuilder buffer, int indent) {
    ClassWrapper wrapper = node.wrapper;
    StructClass cl = wrapper.getClassStruct();
    MethodWrapper methodWrapper = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

    boolean hideMethod = false;

    MethodWrapper outerWrapper = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

    try {
      boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
      boolean isAnnotation = cl.hasModifier(CodeConstants.ACC_ANNOTATION);
      boolean isEnum = cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
      boolean isDeprecated = mt.getAttributes().containsKey("Deprecated");
      boolean clinit = false, init = false, dinit = false;

      String indentString = InterpreterUtil.getIndentString(indent);
      String lineSeparator = DecompilerContext.getNewLineSeparator();

      MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

      int flags = mt.getAccessFlags();
      if ((flags & CodeConstants.ACC_NATIVE) != 0) {
        flags &= ~CodeConstants.ACC_STRICT; // compiler bug: a strictfp class sets all methods to strictfp
      }
      if ("<clinit>".equals(mt.getName())) {
        flags &= CodeConstants.ACC_STATIC; // ignore all modifiers except 'static' in a static initializer
      }

      if (isDeprecated) {
        appendDeprecation(buffer, indentString, lineSeparator);
      }

      if (interceptor != null) {
        String oldName = interceptor.getOldName(cl.qualifiedName + " " + mt.getName() + " " + mt.getDescriptor());
        appendRenameComment(buffer, oldName, MType.METHOD, indent, lineSeparator);
      }

      boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || mt.getAttributes().containsKey("Synthetic");
      boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;
      if (isSynthetic) {
        appendComment(buffer, "synthetic method", indentString, lineSeparator);
      }
      if (isBridge) {
        appendComment(buffer, "bridge method", indentString, lineSeparator);
      }

      appendAnnotations(buffer, mt, indent, lineSeparator);

      buffer.append(indentString);

      appendModifiers(buffer, flags, METHOD_ALLOWED, isInterface, METHOD_EXCLUDED);

      if (isInterface && mt.containsCode()) {
        // 'default' modifier (Java 8)
        buffer.append("default ");
      }

      String name = mt.getName();
      if ("<init>".equals(name)) {
        if (node.type == ClassNode.CLASS_ANONYMOUS) {
          name = "";
          dinit = true;
        }
        else {
          name = node.simpleName;
          init = true;
        }
      }
      else if ("<clinit>".equals(name)) {
        name = "";
        clinit = true;
      }

      GenericMethodDescriptor descriptor = null;
      if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
        StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)mt.getAttributes().getWithKey("Signature");
        if (attr != null) {
          descriptor = GenericMain.parseMethodSignature(attr.getSignature());
          if (descriptor != null) {
            int actualParams = md.params.length;
            if (isEnum && init) actualParams -= 2;
            if (actualParams != descriptor.params.size()) {
              String message = "Inconsistent generic signature in method " + mt.getName() + " " + mt.getDescriptor();
              DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
              descriptor = null;
            }
          }
        }
      }

      boolean throwsExceptions = false;
      int paramCount = 0;

      if (!clinit && !dinit) {
        boolean thisVar = !mt.hasModifier(CodeConstants.ACC_STATIC);

        if (descriptor != null && !descriptor.fparameters.isEmpty()) {
          appendTypeParameters(buffer, descriptor.fparameters, descriptor.fbounds);
          buffer.append(' ');
        }

        if (!init) {
          if (descriptor != null) {
            buffer.append(GenericMain.getGenericCastTypeName(descriptor.ret));
          }
          else {
            buffer.append(ExprProcessor.getCastTypeName(md.ret));
          }
          buffer.append(' ');
        }

        buffer.append(name);
        buffer.append('(');

        // parameters
        List<VarVersionPaar> signFields = methodWrapper.signatureFields;

        int lastVisibleParameterIndex = -1;
        for (int i = 0; i < md.params.length; i++) {
          if (signFields == null || signFields.get(i) == null) {
            lastVisibleParameterIndex = i;
          }
        }

        boolean firstParameter = true;
        int index = isEnum && init ? 3 : thisVar ? 1 : 0;
        int start = isEnum && init && descriptor == null ? 2 : 0;
        int params = descriptor == null ? md.params.length : descriptor.params.size();
        for (int i = start; i < params; i++) {
          if (signFields == null || signFields.get(i) == null) {
            if (!firstParameter) {
              buffer.append(", ");
            }

            appendParameterAnnotations(buffer, mt, paramCount);

            if (methodWrapper.varproc.getVarFinal(new VarVersionPaar(index, 0)) == VarTypeProcessor.VAR_FINALEXPLICIT) {
              buffer.append("final ");
            }

            if (descriptor != null) {
              GenericType parameterType = descriptor.params.get(i);

              boolean isVarArg = (i == lastVisibleParameterIndex && mt.hasModifier(CodeConstants.ACC_VARARGS) && parameterType.arraydim > 0);
              if (isVarArg) {
                parameterType.arraydim--;
              }

              String typeName = GenericMain.getGenericCastTypeName(parameterType);
              if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName) &&
                  DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
                typeName = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
              }

              buffer.append(typeName);

              if (isVarArg) {
                buffer.append("...");
              }
            }
            else {
              VarType parameterType = md.params[i].copy();

              boolean isVarArg = (i == lastVisibleParameterIndex && mt.hasModifier(CodeConstants.ACC_VARARGS) && parameterType.arraydim > 0);
              if (isVarArg) {
                parameterType.decArrayDim();
              }

              String typeName = ExprProcessor.getCastTypeName(parameterType);
              if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName) &&
                  DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
                typeName = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
              }

              buffer.append(typeName);

              if (isVarArg) {
                buffer.append("...");
              }
            }

            buffer.append(' ');
            String parameterName = methodWrapper.varproc.getVarName(new VarVersionPaar(index, 0));
            buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

            firstParameter = false;
            paramCount++;
          }

          index += md.params[i].stack_size;
        }

        buffer.append(')');

        StructExceptionsAttribute attr = (StructExceptionsAttribute)mt.getAttributes().getWithKey("Exceptions");
        if ((descriptor != null && !descriptor.exceptions.isEmpty()) || attr != null) {
          throwsExceptions = true;
          buffer.append(" throws ");

          for (int i = 0; i < attr.getThrowsExceptions().size(); i++) {
            if (i > 0) {
              buffer.append(", ");
            }
            if (descriptor != null && !descriptor.exceptions.isEmpty()) {
              GenericType type = descriptor.exceptions.get(i);
              buffer.append(GenericMain.getGenericCastTypeName(type));
            }
            else {
              VarType type = new VarType(attr.getExcClassname(i, cl.getPool()), true);
              buffer.append(ExprProcessor.getCastTypeName(type));
            }
          }
        }
      }

      if ((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) { // native or abstract method (explicit or interface)
        if (isAnnotation) {
          StructAnnDefaultAttribute attr = (StructAnnDefaultAttribute)mt.getAttributes().getWithKey("AnnotationDefault");
          if (attr != null) {
            buffer.append(" default ");
            buffer.append(attr.getDefaultValue().toJava(indent + 1));
          }
        }

        buffer.append(';');
        buffer.append(lineSeparator);
      }
      else {
        if (!clinit && !dinit) {
          buffer.append(' ');
        }

        buffer.append('{');
        buffer.append(lineSeparator);

        RootStatement root = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;

        if (root != null && !methodWrapper.decompiledWithErrors) { // check for existence
          try {
            String code = root.toJava(indent + 1);

            hideMethod = (clinit || dinit || hideConstructor(wrapper, init, throwsExceptions, paramCount)) && code.length() == 0;

            buffer.append(code);
          }
          catch (Throwable ex) {
            DecompilerContext.getLogger().writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " couldn't be written.", ex);
            methodWrapper.decompiledWithErrors = true;
          }
        }

        if (methodWrapper.decompiledWithErrors) {
          buffer.append(InterpreterUtil.getIndentString(indent + 1));
          buffer.append("// $FF: Couldn't be decompiled");
          buffer.append(lineSeparator);
        }

        buffer.append(indentString);
        buffer.append('}');
        buffer.append(lineSeparator);
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
    }

    return !hideMethod;
  }

  private static boolean hideConstructor(ClassWrapper wrapper, boolean init, boolean throwsExceptions, int paramCount) {
    if (!init || throwsExceptions || paramCount > 0 || !DecompilerContext.getOption(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR)) {
      return false;
    }

    int count = 0;
    for (StructMethod mt : wrapper.getClassStruct().getMethods()) {
      if ("<init>".equals(mt.getName())) {
        if (++count > 1) {
          return false;
        }
      }
    }

    return true;
  }

  private static void appendDeprecation(StringBuilder buffer, String indentString, String lineSeparator) {
    buffer.append(indentString).append("/** @deprecated */").append(lineSeparator);
  }

  private enum MType {CLASS, FIELD, METHOD}

  private static void appendRenameComment(StringBuilder buffer, String oldName, MType type, int indent, String lineSeparator) {
    if (oldName == null) return;

    InterpreterUtil.appendIndent(buffer, indent);
    buffer.append("// $FF: renamed from: ");

    switch (type) {
      case CLASS:
        buffer.append(ExprProcessor.buildJavaClassName(oldName));
        break;

      case FIELD:
        String[] fParts = oldName.split(" ");
        FieldDescriptor fd = FieldDescriptor.parseDescriptor(fParts[2]);
        buffer.append(fParts[1]);
        buffer.append(' ');
        buffer.append(getTypePrintOut(fd.type));
        break;

      default:
        String[] mParts = oldName.split(" ");
        MethodDescriptor md = MethodDescriptor.parseDescriptor(mParts[2]);
        buffer.append(mParts[1]);
        buffer.append(" (");
        boolean first = true;
        for (VarType paramType : md.params) {
          if (!first) {
            buffer.append(", ");
          }
          first = false;
          buffer.append(getTypePrintOut(paramType));
        }
        buffer.append(") ");
        buffer.append(getTypePrintOut(md.ret));
    }

    buffer.append(lineSeparator);
  }

  private static String getTypePrintOut(VarType type) {
    String typeText = ExprProcessor.getCastTypeName(type, false);
    if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeText) &&
        DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
      typeText = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT, false);
    }
    return typeText;
  }

  private static void appendComment(StringBuilder buffer, String comment, String indentString, String lineSeparator) {
    buffer.append(indentString).append("// $FF: ").append(comment).append(lineSeparator);
  }

  private static final String[] ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS};

  private static void appendAnnotations(StringBuilder buffer, StructMember mb, int indent, String lineSeparator) {
    for (String name : ANNOTATION_ATTRIBUTES) {
      StructAnnotationAttribute attribute = (StructAnnotationAttribute)mb.getAttributes().getWithKey(name);
      if (attribute != null) {
        for (AnnotationExprent annotation : attribute.getAnnotations()) {
          buffer.append(annotation.toJava(indent)).append(lineSeparator);
        }
      }
    }
  }

  private static final String[] PARAMETER_ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS};

  private static void appendParameterAnnotations(StringBuilder buffer, StructMethod mt, int param) {
    for (String name : PARAMETER_ANNOTATION_ATTRIBUTES) {
      StructAnnotationParameterAttribute attribute = (StructAnnotationParameterAttribute)mt.getAttributes().getWithKey(name);
      if (attribute != null) {
        List<List<AnnotationExprent>> annotations = attribute.getParamAnnotations();
        if (param < annotations.size()) {
          for (AnnotationExprent annotation : annotations.get(param)) {
            buffer.append(annotation.toJava(0)).append(' ');
          }
        }
      }
    }
  }

  private static final Map<Integer, String> MODIFIERS = new LinkedHashMap<Integer, String>() {{
    put(CodeConstants.ACC_PUBLIC, "public");
    put(CodeConstants.ACC_PROTECTED, "protected");
    put(CodeConstants.ACC_PRIVATE, "private");
    put(CodeConstants.ACC_ABSTRACT, "abstract");
    put(CodeConstants.ACC_STATIC, "static");
    put(CodeConstants.ACC_FINAL, "final");
    put(CodeConstants.ACC_STRICT, "strictfp");
    put(CodeConstants.ACC_TRANSIENT, "transient");
    put(CodeConstants.ACC_VOLATILE, "volatile");
    put(CodeConstants.ACC_SYNCHRONIZED, "synchronized");
    put(CodeConstants.ACC_NATIVE, "native");
  }};

  private static final int CLASS_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_ABSTRACT |
    CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL | CodeConstants.ACC_STRICT;
  private static final int FIELD_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_STATIC |
    CodeConstants.ACC_FINAL | CodeConstants.ACC_TRANSIENT | CodeConstants.ACC_VOLATILE;
  private static final int METHOD_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_ABSTRACT |
    CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL | CodeConstants.ACC_SYNCHRONIZED | CodeConstants.ACC_NATIVE | CodeConstants.ACC_STRICT;

  private static final int CLASS_EXCLUDED = CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_STATIC;
  private static final int FIELD_EXCLUDED = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL;
  private static final int METHOD_EXCLUDED = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_ABSTRACT;

  private static void appendModifiers(StringBuilder buffer, int flags, int allowed, boolean isInterface, int excluded) {
    flags &= allowed;
    if (!isInterface) excluded = 0;
    for (int modifier : MODIFIERS.keySet()) {
      if ((flags & modifier) == modifier && (modifier & excluded) == 0) {
        buffer.append(MODIFIERS.get(modifier)).append(' ');
      }
    }
  }

  private static void appendTypeParameters(StringBuilder buffer, List<String> parameters, List<List<GenericType>> bounds) {
    buffer.append('<');

    for (int i = 0; i < parameters.size(); i++) {
      if (i > 0) {
        buffer.append(", ");
      }

      buffer.append(parameters.get(i));

      List<GenericType> parameterBounds = bounds.get(i);
      if (parameterBounds.size() > 1 || !"java/lang/Object".equals(parameterBounds.get(0).value)) {
        buffer.append(" extends ");
        buffer.append(GenericMain.getGenericCastTypeName(parameterBounds.get(0)));
        for (int j = 1; j < parameterBounds.size(); j++) {
          buffer.append(" & ");
          buffer.append(GenericMain.getGenericCastTypeName(parameterBounds.get(j)));
        }
      }
    }

    buffer.append('>');
  }
}
