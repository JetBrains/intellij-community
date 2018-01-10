/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarTypeProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
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
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;

public class ClassWriter {
  private final PoolInterceptor interceptor;

  public ClassWriter() {
    interceptor = DecompilerContext.getPoolInterceptor();
  }

  private static void invokeProcessors(ClassNode node) {
    ClassWrapper wrapper = node.getWrapper();
    StructClass cl = wrapper.getClassStruct();

    InitializerProcessor.extractInitializers(wrapper);

    if (node.type == ClassNode.CLASS_ROOT &&
        !cl.isVersionGE_1_5() &&
        DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_CLASS_1_4)) {
      ClassReference14Processor.processClassReferences(node);
    }

    if (cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM)) {
      EnumProcessor.clearEnum(wrapper);
    }

    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ASSERTIONS)) {
      AssertProcessor.buildAssertions(node);
    }
  }

  public void classLambdaToJava(ClassNode node, TextBuffer buffer, Exprent method_object, int indent, BytecodeMappingTracer origTracer) {
    ClassWrapper wrapper = node.getWrapper();
    if (wrapper == null) {
      return;
    }

    boolean lambdaToAnonymous = DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS);

    ClassNode outerNode = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

    BytecodeMappingTracer tracer = new BytecodeMappingTracer(origTracer.getCurrentSourceLine());

    try {
      StructClass cl = wrapper.getClassStruct();

      DecompilerContext.getLogger().startWriteClass(node.simpleName);

      if (node.lambdaInformation.is_method_reference) {
        if (!node.lambdaInformation.is_content_method_static && method_object != null) {
          // reference to a virtual method
          buffer.append(method_object.toJava(indent, tracer));
        }
        else {
          // reference to a static method
          buffer.append(ExprProcessor.getCastTypeName(new VarType(node.lambdaInformation.content_class_name, true)));
        }

        buffer.append("::")
          .append(CodeConstants.INIT_NAME.equals(node.lambdaInformation.content_method_name) ? "new" : node.lambdaInformation.content_method_name);
      }
      else {
        // lambda method
        StructMethod mt = cl.getMethod(node.lambdaInformation.content_method_key);
        MethodWrapper methodWrapper = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());
        MethodDescriptor md_content = MethodDescriptor.parseDescriptor(node.lambdaInformation.content_method_descriptor);
        MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(node.lambdaInformation.method_descriptor);

        if (!lambdaToAnonymous) {
          buffer.append('(');

          boolean firstParameter = true;
          int index = node.lambdaInformation.is_content_method_static ? 0 : 1;
          int start_index = md_content.params.length - md_lambda.params.length;

          for (int i = 0; i < md_content.params.length; i++) {
            if (i >= start_index) {
              if (!firstParameter) {
                buffer.append(", ");
              }

              String parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
              buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

              firstParameter = false;
            }

            index += md_content.params[i].stackSize;
          }

          buffer.append(") ->");
        }

        buffer.append(" {").appendLineSeparator();
        tracer.incrementCurrentSourceLine();

        methodLambdaToJava(node, wrapper, mt, buffer, indent + 1, !lambdaToAnonymous, tracer);

        buffer.appendIndent(indent).append("}");

        addTracer(cl, mt, tracer);
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, outerNode);
    }

    DecompilerContext.getLogger().endWriteClass();
  }

  public void classToJava(ClassNode node, TextBuffer buffer, int indent, BytecodeMappingTracer tracer) {
    ClassNode outerNode = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

    int startLine = tracer != null ? tracer.getCurrentSourceLine() : 0;
    BytecodeMappingTracer dummy_tracer = new BytecodeMappingTracer(startLine);

    try {
      // last minute processing
      invokeProcessors(node);

      ClassWrapper wrapper = node.getWrapper();
      StructClass cl = wrapper.getClassStruct();

      DecompilerContext.getLogger().startWriteClass(cl.qualifiedName);

      // write class definition
      int start_class_def = buffer.length();
      writeClassDefinition(node, buffer, indent);

      boolean hasContent = false;
      boolean enumFields = false;

      dummy_tracer.incrementCurrentSourceLine(buffer.countLines(start_class_def));

      for (StructField fd : cl.getFields()) {
        boolean hide = fd.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
                       wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
        if (hide) continue;

        boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
        if (isEnum) {
          if (enumFields) {
            buffer.append(',').appendLineSeparator();
            dummy_tracer.incrementCurrentSourceLine();
          }
          enumFields = true;
        }
        else if (enumFields) {
          buffer.append(';');
          buffer.appendLineSeparator();
          buffer.appendLineSeparator();
          dummy_tracer.incrementCurrentSourceLine(2);
          enumFields = false;
        }

        fieldToJava(wrapper, cl, fd, buffer, indent + 1, dummy_tracer); // FIXME: insert real tracer

        hasContent = true;
      }

      if (enumFields) {
        buffer.append(';').appendLineSeparator();
        dummy_tracer.incrementCurrentSourceLine();
      }

      // FIXME: fields don't matter at the moment
      startLine += buffer.countLines(start_class_def);

      // methods
      for (StructMethod mt : cl.getMethods()) {
        boolean hide = mt.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
                       mt.hasModifier(CodeConstants.ACC_BRIDGE) && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_BRIDGE) ||
                       wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
        if (hide) continue;

        int position = buffer.length();
        int storedLine = startLine;
        if (hasContent) {
          buffer.appendLineSeparator();
          startLine++;
        }
        BytecodeMappingTracer method_tracer = new BytecodeMappingTracer(startLine);
        boolean methodSkipped = !methodToJava(node, mt, buffer, indent + 1, method_tracer);
        if (!methodSkipped) {
          hasContent = true;
          addTracer(cl, mt, method_tracer);
          startLine = method_tracer.getCurrentSourceLine();
        }
        else {
          buffer.setLength(position);
          startLine = storedLine;
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
            buffer.appendLineSeparator();
            startLine++;
          }
          BytecodeMappingTracer class_tracer = new BytecodeMappingTracer(startLine);
          classToJava(inner, buffer, indent + 1, class_tracer);
          startLine = buffer.countLines();

          hasContent = true;
        }
      }

      buffer.appendIndent(indent).append('}');

      if (node.type != ClassNode.CLASS_ANONYMOUS) {
        buffer.appendLineSeparator();
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, outerNode);
    }

    DecompilerContext.getLogger().endWriteClass();
  }

  private static void addTracer(StructClass cls, StructMethod method, BytecodeMappingTracer tracer) {
    StructLineNumberTableAttribute table =
      (StructLineNumberTableAttribute)method.getAttribute(StructGeneralAttribute.ATTRIBUTE_LINE_NUMBER_TABLE);
    tracer.setLineNumberTable(table);
    String key = InterpreterUtil.makeUniqueKey(method.getName(), method.getDescriptor());
    DecompilerContext.getBytecodeSourceMapper().addTracer(cls.qualifiedName, key, tracer);
  }

  private void writeClassDefinition(ClassNode node, TextBuffer buffer, int indent) {
    if (node.type == ClassNode.CLASS_ANONYMOUS) {
      buffer.append(" {").appendLineSeparator();
      return;
    }

    ClassWrapper wrapper = node.getWrapper();
    StructClass cl = wrapper.getClassStruct();

    int flags = node.type == ClassNode.CLASS_ROOT ? cl.getAccessFlags() : node.access;
    boolean isDeprecated = cl.hasAttribute("Deprecated");
    boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || cl.hasAttribute("Synthetic");
    boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;
    boolean isInterface = (flags & CodeConstants.ACC_INTERFACE) != 0;
    boolean isAnnotation = (flags & CodeConstants.ACC_ANNOTATION) != 0;

    if (isDeprecated) {
      appendDeprecation(buffer, indent);
    }

    if (interceptor != null) {
      String oldName = interceptor.getOldName(cl.qualifiedName);
      appendRenameComment(buffer, oldName, MType.CLASS, indent);
    }

    if (isSynthetic) {
      appendComment(buffer, "synthetic class", indent);
    }

    appendAnnotations(buffer, indent, cl, -1);

    buffer.appendIndent(indent);

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

    buffer.append(node.simpleName);

    GenericClassDescriptor descriptor = getGenericClassDescriptor(cl);
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

    buffer.append('{').appendLineSeparator();
  }

  private void fieldToJava(ClassWrapper wrapper, StructClass cl, StructField fd, TextBuffer buffer, int indent, BytecodeMappingTracer tracer) {
    int start = buffer.length();
    boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
    boolean isDeprecated = fd.hasAttribute("Deprecated");
    boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);

    if (isDeprecated) {
      appendDeprecation(buffer, indent);
    }

    if (interceptor != null) {
      String oldName = interceptor.getOldName(cl.qualifiedName + " " + fd.getName() + " " + fd.getDescriptor());
      appendRenameComment(buffer, oldName, MType.FIELD, indent);
    }

    if (fd.isSynthetic()) {
      appendComment(buffer, "synthetic field", indent);
    }

    appendAnnotations(buffer, indent, fd, TypeAnnotation.FIELD);

    buffer.appendIndent(indent);

    if (!isEnum) {
      appendModifiers(buffer, fd.getAccessFlags(), FIELD_ALLOWED, isInterface, FIELD_EXCLUDED);
    }

    VarType fieldType = new VarType(fd.getDescriptor(), false);

    GenericFieldDescriptor descriptor = null;
    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
      StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)fd.getAttribute("Signature");
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

    tracer.incrementCurrentSourceLine(buffer.countLines(start));

    Exprent initializer;
    if (fd.hasModifier(CodeConstants.ACC_STATIC)) {
      initializer = wrapper.getStaticFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    }
    else {
      initializer = wrapper.getDynamicFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    }
    if (initializer != null) {
      if (isEnum && initializer.type == Exprent.EXPRENT_NEW) {
        NewExprent expr = (NewExprent)initializer;
        expr.setEnumConst(true);
        buffer.append(expr.toJava(indent, tracer));
      }
      else {
        buffer.append(" = ");

        if (initializer.type == Exprent.EXPRENT_CONST) {
          ((ConstExprent) initializer).adjustConstType(fieldType);
        }

        // FIXME: special case field initializer. Can map to more than one method (constructor) and bytecode instruction
        buffer.append(initializer.toJava(indent, tracer));
      }
    }
    else if (fd.hasModifier(CodeConstants.ACC_FINAL) && fd.hasModifier(CodeConstants.ACC_STATIC)) {
      StructConstantValueAttribute attr =
        (StructConstantValueAttribute)fd.getAttribute(StructGeneralAttribute.ATTRIBUTE_CONSTANT_VALUE);
      if (attr != null) {
        PrimitiveConstant constant = cl.getPool().getPrimitiveConstant(attr.getIndex());
        buffer.append(" = ");
        buffer.append(new ConstExprent(fieldType, constant.value, null).toJava(indent, tracer));
      }
    }

    if (!isEnum) {
      buffer.append(";").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }
  }

  private static void methodLambdaToJava(ClassNode lambdaNode,
                                         ClassWrapper classWrapper,
                                         StructMethod mt,
                                         TextBuffer buffer,
                                         int indent,
                                         boolean codeOnly, BytecodeMappingTracer tracer) {
    MethodWrapper methodWrapper = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

    MethodWrapper outerWrapper = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

    try {
      String method_name = lambdaNode.lambdaInformation.method_name;
      MethodDescriptor md_content = MethodDescriptor.parseDescriptor(lambdaNode.lambdaInformation.content_method_descriptor);
      MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(lambdaNode.lambdaInformation.method_descriptor);

      if (!codeOnly) {
        buffer.appendIndent(indent);
        buffer.append("public ");
        buffer.append(method_name);
        buffer.append("(");

        boolean firstParameter = true;
        int index = lambdaNode.lambdaInformation.is_content_method_static ? 0 : 1;
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

            String parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
            buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

            firstParameter = false;
          }

          index += md_content.params[i].stackSize;
        }

        buffer.append(") {").appendLineSeparator();

        indent += 1;
      }

      RootStatement root = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;
      if (!methodWrapper.decompiledWithErrors) {
        if (root != null) { // check for existence
          try {
            buffer.append(root.toJava(indent, tracer));
          }
          catch (Throwable ex) {
            DecompilerContext.getLogger().writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " couldn't be written.",
                                                       IFernflowerLogger.Severity.WARN,
                                                       ex);
            methodWrapper.decompiledWithErrors = true;
          }
        }
      }

      if (methodWrapper.decompiledWithErrors) {
        buffer.appendIndent(indent);
        buffer.append("// $FF: Couldn't be decompiled");
        buffer.appendLineSeparator();
      }

      if (root != null) {
        tracer.addMapping(root.getDummyExit().bytecode);
      }

      if (!codeOnly) {
        indent -= 1;
        buffer.appendIndent(indent).append('}').appendLineSeparator();
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
    }
  }

  private static String toValidJavaIdentifier(String name) {
    if (name == null || name.isEmpty()) return name;

    boolean changed = false;
    StringBuilder res = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if ((i == 0 && !Character.isJavaIdentifierStart(c))
          || (i > 0 && !Character.isJavaIdentifierPart(c))) {
        changed = true;
        res.append("_");
      }
      else {
        res.append(c);
      }
    }
    if (!changed) {
      return name;
    }
    return res.append("/* $FF was: ").append(name).append("*/").toString();
  }

  private boolean methodToJava(ClassNode node, StructMethod mt, TextBuffer buffer, int indent, BytecodeMappingTracer tracer) {
    ClassWrapper wrapper = node.getWrapper();
    StructClass cl = wrapper.getClassStruct();
    MethodWrapper methodWrapper = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

    boolean hideMethod = false;
    int start_index_method = buffer.length();

    MethodWrapper outerWrapper = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

    try {
      boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
      boolean isAnnotation = cl.hasModifier(CodeConstants.ACC_ANNOTATION);
      boolean isEnum = cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
      boolean isDeprecated = mt.hasAttribute("Deprecated");
      boolean clinit = false, init = false, dinit = false;

      MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

      int flags = mt.getAccessFlags();
      if ((flags & CodeConstants.ACC_NATIVE) != 0) {
        flags &= ~CodeConstants.ACC_STRICT; // compiler bug: a strictfp class sets all methods to strictfp
      }
      if (CodeConstants.CLINIT_NAME.equals(mt.getName())) {
        flags &= CodeConstants.ACC_STATIC; // ignore all modifiers except 'static' in a static initializer
      }

      if (isDeprecated) {
        appendDeprecation(buffer, indent);
      }

      if (interceptor != null) {
        String oldName = interceptor.getOldName(cl.qualifiedName + " " + mt.getName() + " " + mt.getDescriptor());
        appendRenameComment(buffer, oldName, MType.METHOD, indent);
      }

      boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || mt.hasAttribute("Synthetic");
      boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;
      if (isSynthetic) {
        appendComment(buffer, "synthetic method", indent);
      }
      if (isBridge) {
        appendComment(buffer, "bridge method", indent);
      }

      appendAnnotations(buffer, indent, mt, TypeAnnotation.METHOD_RETURN_TYPE);

      buffer.appendIndent(indent);

      appendModifiers(buffer, flags, METHOD_ALLOWED, isInterface, METHOD_EXCLUDED);

      if (isInterface && !mt.hasModifier(CodeConstants.ACC_STATIC) && mt.containsCode()) {
        // 'default' modifier (Java 8)
        buffer.append("default ");
      }

      String name = mt.getName();
      if (CodeConstants.INIT_NAME.equals(name)) {
        if (node.type == ClassNode.CLASS_ANONYMOUS) {
          name = "";
          dinit = true;
        }
        else {
          name = node.simpleName;
          init = true;
        }
      }
      else if (CodeConstants.CLINIT_NAME.equals(name)) {
        name = "";
        clinit = true;
      }

      GenericMethodDescriptor descriptor = null;
      if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
        StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)mt.getAttribute("Signature");
        if (attr != null) {
          descriptor = GenericMain.parseMethodSignature(attr.getSignature());
          if (descriptor != null) {
            long actualParams = md.params.length;
            List<VarVersionPair> mask = methodWrapper.synthParameters;
            if (mask != null) {
              actualParams = mask.stream().filter(Objects::isNull).count();
            }
            else if (isEnum && init) {
              actualParams -= 2;
            }
            if (actualParams != descriptor.parameterTypes.size()) {
              String message = "Inconsistent generic signature in method " + mt.getName() + " " + mt.getDescriptor() + " in " + cl.qualifiedName;
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

        if (descriptor != null && !descriptor.typeParameters.isEmpty()) {
          appendTypeParameters(buffer, descriptor.typeParameters, descriptor.typeParameterBounds);
          buffer.append(' ');
        }

        if (!init) {
          if (descriptor != null) {
            buffer.append(GenericMain.getGenericCastTypeName(descriptor.returnType));
          }
          else {
            buffer.append(ExprProcessor.getCastTypeName(md.ret));
          }
          buffer.append(' ');
        }

        buffer.append(toValidJavaIdentifier(name));
        buffer.append('(');

        List<VarVersionPair> mask = methodWrapper.synthParameters;

        int lastVisibleParameterIndex = -1;
        for (int i = 0; i < md.params.length; i++) {
          if (mask == null || mask.get(i) == null) {
            lastVisibleParameterIndex = i;
          }
        }

        List<StructMethodParametersAttribute.Entry> methodParameters = null;
        if (DecompilerContext.getOption(IFernflowerPreferences.USE_METHOD_PARAMETERS)) {
          StructMethodParametersAttribute attr =
            (StructMethodParametersAttribute)mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_METHOD_PARAMETERS);
          if (attr != null) {
            methodParameters = attr.getEntries();
          }
        }

        int index = isEnum && init ? 3 : thisVar ? 1 : 0;
        int start = isEnum && init ? 2 : 0;
        for (int i = start; i < md.params.length; i++) {
          if (mask == null || mask.get(i) == null) {
            if (paramCount > 0) {
              buffer.append(", ");
            }

            appendParameterAnnotations(buffer, mt, paramCount);

            if (methodParameters != null && i < methodParameters.size()) {
              appendModifiers(buffer, methodParameters.get(i).myAccessFlags, CodeConstants.ACC_FINAL, isInterface, 0);
            }
            else if (methodWrapper.varproc.getVarFinal(new VarVersionPair(index, 0)) == VarTypeProcessor.VAR_EXPLICIT_FINAL) {
              buffer.append("final ");
            }

            String typeName;
            boolean isVarArg = i == lastVisibleParameterIndex && mt.hasModifier(CodeConstants.ACC_VARARGS);

            if (descriptor != null) {
              GenericType parameterType = descriptor.parameterTypes.get(paramCount);
              isVarArg &= parameterType.arrayDim > 0;
              if (isVarArg) {
                parameterType = parameterType.decreaseArrayDim();
              }
              typeName = GenericMain.getGenericCastTypeName(parameterType);
            }
            else {
              VarType parameterType = md.params[i];
              isVarArg &= parameterType.arrayDim > 0;
              if (isVarArg) {
                parameterType = parameterType.decreaseArrayDim();
              }
              typeName = ExprProcessor.getCastTypeName(parameterType);
            }

            if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName) &&
                DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
              typeName = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
            }
            buffer.append(typeName);
            if (isVarArg) {
              buffer.append("...");
            }

            buffer.append(' ');

            String parameterName;
            if (methodParameters != null && i < methodParameters.size()) {
              parameterName = methodParameters.get(i).myName;
            }
            else {
              parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
            }
            buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

            paramCount++;
          }

          index += md.params[i].stackSize;
        }

        buffer.append(')');

        StructExceptionsAttribute attr = (StructExceptionsAttribute)mt.getAttribute("Exceptions");
        if ((descriptor != null && !descriptor.exceptionTypes.isEmpty()) || attr != null) {
          throwsExceptions = true;
          buffer.append(" throws ");

          for (int i = 0; i < attr.getThrowsExceptions().size(); i++) {
            if (i > 0) {
              buffer.append(", ");
            }
            if (descriptor != null && !descriptor.exceptionTypes.isEmpty()) {
              GenericType type = descriptor.exceptionTypes.get(i);
              buffer.append(GenericMain.getGenericCastTypeName(type));
            }
            else {
              VarType type = new VarType(attr.getExcClassname(i, cl.getPool()), true);
              buffer.append(ExprProcessor.getCastTypeName(type));
            }
          }
        }
      }

      tracer.incrementCurrentSourceLine(buffer.countLines(start_index_method));

      if ((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) { // native or abstract method (explicit or interface)
        if (isAnnotation) {
          StructAnnDefaultAttribute attr = (StructAnnDefaultAttribute)mt.getAttribute("AnnotationDefault");
          if (attr != null) {
            buffer.append(" default ");
            buffer.append(attr.getDefaultValue().toJava(0, BytecodeMappingTracer.DUMMY));
          }
        }

        buffer.append(';');
        buffer.appendLineSeparator();
        tracer.incrementCurrentSourceLine();
      }
      else {
        if (!clinit && !dinit) {
          buffer.append(' ');
        }

        // We do not have line information for method start, lets have it here for now
        buffer.append('{').appendLineSeparator();
        tracer.incrementCurrentSourceLine();

        RootStatement root = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;

        if (root != null && !methodWrapper.decompiledWithErrors) { // check for existence
          try {
            // to restore in case of an exception
            BytecodeMappingTracer codeTracer = new BytecodeMappingTracer(tracer.getCurrentSourceLine());
            TextBuffer code = root.toJava(indent + 1, codeTracer);

            hideMethod = (clinit || dinit || hideConstructor(wrapper, init, throwsExceptions, paramCount)) && code.length() == 0;

            buffer.append(code);

            tracer.setCurrentSourceLine(codeTracer.getCurrentSourceLine());
            tracer.addTracer(codeTracer);
          }
          catch (Throwable ex) {
            DecompilerContext.getLogger()
              .writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " couldn't be written.",
                            IFernflowerLogger.Severity.WARN,
                            ex);
            methodWrapper.decompiledWithErrors = true;
          }
        }

        if (methodWrapper.decompiledWithErrors) {
          buffer.appendIndent(indent + 1);
          buffer.append("// $FF: Couldn't be decompiled");
          buffer.appendLineSeparator();
          tracer.incrementCurrentSourceLine();
        }
        else if (root != null) {
          tracer.addMapping(root.getDummyExit().bytecode);
        }
        buffer.appendIndent(indent).append('}').appendLineSeparator();
        tracer.incrementCurrentSourceLine();
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
    }

    // save total lines
    // TODO: optimize
    //tracer.setCurrentSourceLine(buffer.countLines(start_index_method));

    return !hideMethod;
  }

  private static boolean hideConstructor(ClassWrapper wrapper, boolean init, boolean throwsExceptions, int paramCount) {
    if (!init || throwsExceptions || paramCount > 0 || !DecompilerContext.getOption(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR)) {
      return false;
    }

    int count = 0;
    for (StructMethod mt : wrapper.getClassStruct().getMethods()) {
      if (CodeConstants.INIT_NAME.equals(mt.getName())) {
        if (++count > 1) {
          return false;
        }
      }
    }

    return true;
  }

  private static void appendDeprecation(TextBuffer buffer, int indent) {
    buffer.appendIndent(indent).append("/** @deprecated */").appendLineSeparator();
  }

  private enum MType {CLASS, FIELD, METHOD}

  private static void appendRenameComment(TextBuffer buffer, String oldName, MType type, int indent) {
    if (oldName == null) return;

    buffer.appendIndent(indent);
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

    buffer.appendLineSeparator();
  }

  private static String getTypePrintOut(VarType type) {
    String typeText = ExprProcessor.getCastTypeName(type, false);
    if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeText) &&
        DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
      typeText = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT, false);
    }
    return typeText;
  }

  private static void appendComment(TextBuffer buffer, String comment, int indent) {
    buffer.appendIndent(indent).append("// $FF: ").append(comment).appendLineSeparator();
  }

  private static final String[] ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS};
  private static final String[] PARAMETER_ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS};
  private static final String[] TYPE_ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS};

  private static void appendAnnotations(TextBuffer buffer, int indent, StructMember mb, int targetType) {
    Set<String> filter = new HashSet<>();

    for (String name : ANNOTATION_ATTRIBUTES) {
      StructAnnotationAttribute attribute = (StructAnnotationAttribute)mb.getAttribute(name);
      if (attribute != null) {
        for (AnnotationExprent annotation : attribute.getAnnotations()) {
          String text = annotation.toJava(indent, BytecodeMappingTracer.DUMMY).toString();
          filter.add(text);
          buffer.append(text).appendLineSeparator();
        }
      }
    }

    appendTypeAnnotations(buffer, indent, mb, targetType, -1, filter);
  }

  private static void appendParameterAnnotations(TextBuffer buffer, StructMethod mt, int param) {
    Set<String> filter = new HashSet<>();

    for (String name : PARAMETER_ANNOTATION_ATTRIBUTES) {
      StructAnnotationParameterAttribute attribute = (StructAnnotationParameterAttribute)mt.getAttribute(name);
      if (attribute != null) {
        List<List<AnnotationExprent>> annotations = attribute.getParamAnnotations();
        if (param < annotations.size()) {
          for (AnnotationExprent annotation : annotations.get(param)) {
            String text = annotation.toJava(-1, BytecodeMappingTracer.DUMMY).toString();
            filter.add(text);
            buffer.append(text).append(' ');
          }
        }
      }
    }

    appendTypeAnnotations(buffer, -1, mt, TypeAnnotation.METHOD_PARAMETER, param, filter);
  }

  private static void appendTypeAnnotations(TextBuffer buffer, int indent, StructMember mb, int targetType, int index, Set<String> filter) {
    for (String name : TYPE_ANNOTATION_ATTRIBUTES) {
      StructTypeAnnotationAttribute attribute = (StructTypeAnnotationAttribute)mb.getAttribute(name);
      if (attribute != null) {
        for (TypeAnnotation annotation : attribute.getAnnotations()) {
          if (annotation.isTopLevel() && annotation.getTargetType() == targetType && (index < 0 || annotation.getIndex() == index)) {
            String text = annotation.getAnnotation().toJava(indent, BytecodeMappingTracer.DUMMY).toString();
            if (!filter.contains(text)) {
              buffer.append(text);
              if (indent < 0) {
                buffer.append(' ');
              }
              else {
                buffer.appendLineSeparator();
              }
            }
          }
        }
      }
    }
  }

  private static final Map<Integer, String> MODIFIERS;
  static {
    MODIFIERS = new LinkedHashMap<>();
    MODIFIERS.put(CodeConstants.ACC_PUBLIC, "public");
    MODIFIERS.put(CodeConstants.ACC_PROTECTED, "protected");
    MODIFIERS.put(CodeConstants.ACC_PRIVATE, "private");
    MODIFIERS.put(CodeConstants.ACC_ABSTRACT, "abstract");
    MODIFIERS.put(CodeConstants.ACC_STATIC, "static");
    MODIFIERS.put(CodeConstants.ACC_FINAL, "final");
    MODIFIERS.put(CodeConstants.ACC_STRICT, "strictfp");
    MODIFIERS.put(CodeConstants.ACC_TRANSIENT, "transient");
    MODIFIERS.put(CodeConstants.ACC_VOLATILE, "volatile");
    MODIFIERS.put(CodeConstants.ACC_SYNCHRONIZED, "synchronized");
    MODIFIERS.put(CodeConstants.ACC_NATIVE, "native");
  }

  private static final int CLASS_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_ABSTRACT |
    CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL | CodeConstants.ACC_STRICT;
  private static final int FIELD_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_STATIC |
    CodeConstants.ACC_FINAL | CodeConstants.ACC_TRANSIENT | CodeConstants.ACC_VOLATILE;
  private static final int METHOD_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_ABSTRACT |
    CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL | CodeConstants.ACC_SYNCHRONIZED | CodeConstants.ACC_NATIVE |
    CodeConstants.ACC_STRICT;

  private static final int CLASS_EXCLUDED = CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_STATIC;
  private static final int FIELD_EXCLUDED = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL;
  private static final int METHOD_EXCLUDED = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_ABSTRACT;

  private static void appendModifiers(TextBuffer buffer, int flags, int allowed, boolean isInterface, int excluded) {
    flags &= allowed;
    if (!isInterface) excluded = 0;
    for (int modifier : MODIFIERS.keySet()) {
      if ((flags & modifier) == modifier && (modifier & excluded) == 0) {
        buffer.append(MODIFIERS.get(modifier)).append(' ');
      }
    }
  }

  public static GenericClassDescriptor getGenericClassDescriptor(StructClass cl) {
    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
      StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)cl.getAttribute("Signature");
      if (attr != null) {
        return GenericMain.parseClassSignature(attr.getSignature());
      }
    }
    return null;
  }

  public static void appendTypeParameters(TextBuffer buffer, List<String> parameters, List<List<GenericType>> bounds) {
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