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
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.*;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.*;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class ClassWriter {

  private static final int[] modval_class = new int[]{CodeConstants.ACC_PUBLIC, CodeConstants.ACC_PROTECTED, CodeConstants.ACC_PRIVATE,
    CodeConstants.ACC_ABSTRACT, CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL, CodeConstants.ACC_STRICT};

  private static final String[] modstr_class =
    new String[]{"public ", "protected ", "private ", "abstract ", "static ", "final ", "strictfp "};

  private static final int[] modval_field = new int[]{CodeConstants.ACC_PUBLIC, CodeConstants.ACC_PROTECTED, CodeConstants.ACC_PRIVATE,
    CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL, CodeConstants.ACC_TRANSIENT, CodeConstants.ACC_VOLATILE};

  private static final String[] modstr_field =
    new String[]{"public ", "protected ", "private ", "static ", "final ", "transient ", "volatile "};

  private static final int[] modval_meth = new int[]{CodeConstants.ACC_PUBLIC, CodeConstants.ACC_PROTECTED, CodeConstants.ACC_PRIVATE,
    CodeConstants.ACC_ABSTRACT, CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL, CodeConstants.ACC_SYNCHRONIZED,
    CodeConstants.ACC_NATIVE, CodeConstants.ACC_STRICT};

  private static final String[] modstr_meth =
    new String[]{"public ", "protected ", "private ", "abstract ", "static ", "final ", "synchronized ", "native ", "strictfp "};

  private static final HashSet<Integer> mod_notinterface =
    new HashSet<Integer>(Arrays.asList(new Integer[]{CodeConstants.ACC_ABSTRACT, CodeConstants.ACC_STATIC}));
  private static final HashSet<Integer> mod_notinterface_fields =
    new HashSet<Integer>(Arrays.asList(new Integer[]{CodeConstants.ACC_PUBLIC, CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL}));
  private static final HashSet<Integer> mod_notinterface_meth =
    new HashSet<Integer>(Arrays.asList(new Integer[]{CodeConstants.ACC_PUBLIC, CodeConstants.ACC_ABSTRACT}));

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

    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (cl.access_flags & CodeConstants.ACC_ENUM) != 0) {
      EnumProcessor.clearEnum(wrapper);
    }

    if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ASSERTIONS)) {
      AssertProcessor.buildAssertions(node);
    }
  }

  public void classLambdaToJava(ClassNode node, BufferedWriter writer, Exprent method_object, int indent) throws IOException {

    // get the class node with the content method
    ClassNode node_content = node;
    while (node_content != null && node_content.type == ClassNode.CLASS_LAMBDA) {
      node_content = node_content.parent;
    }

    if (node_content == null) {
      return;
    }

    boolean lambda_to_anonymous = DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS);

    ClassNode nodeold = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASSNODE, node);

    ClassWrapper wrapper = node_content.wrapper;
    StructClass cl = wrapper.getClassStruct();

    DecompilerContext.getLogger().startWriteClass(node.simpleName);

    if (node.lambda_information.is_method_reference) {

      if (!node.lambda_information.is_content_method_static && method_object != null) { // reference to a virtual method
        writer.write(method_object.toJava(indent));
      }
      else { // reference to a static method
        writer.write(ExprProcessor.getCastTypeName(new VarType(node.lambda_information.content_class_name, false)));
      }

      writer.write("::");
      writer.write(node.lambda_information.content_method_name);

      writer.flush();
    }
    else {

      // lambda method
      StructMethod mt = cl.getMethod(node.lambda_information.content_method_key);
      MethodWrapper meth = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

      MethodDescriptor md_content = MethodDescriptor.parseDescriptor(node.lambda_information.content_method_descriptor);
      MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(node.lambda_information.method_descriptor);

      if (!lambda_to_anonymous) { // lambda parameters '() ->'

        StringBuilder buff = new StringBuilder("(");

        boolean firstpar = true;
        int index = node.lambda_information.is_content_method_static ? 0 : 1;
        ;

        int start_index = md_content.params.length - md_lambda.params.length;

        for (int i = 0; i < md_content.params.length; i++) {

          if (i >= start_index) {

            if (!firstpar) {
              buff.append(", ");
            }

            String parname = meth.varproc.getVarName(new VarVersionPaar(index, 0));
            buff.append(parname == null ? "param" + index : parname); // null iff decompiled with errors

            firstpar = false;
          }

          index += md_content.params[i].stack_size;
        }
        buff.append(") ->");

        writer.write(buff.toString());
      }

      StringWriter strwriter = new StringWriter();
      BufferedWriter bufstrwriter = new BufferedWriter(strwriter);

      if (lambda_to_anonymous) {
        methodLambdaToJava(node, node_content, mt, bufstrwriter, indent + 1, false);
      }
      else {
        methodLambdaToJava(node, node_content, mt, bufstrwriter, indent, true);
      }

      bufstrwriter.flush();

      // closing up class definition
      writer.write(" {");
      writer.write(DecompilerContext.getNewLineSeparator());

      writer.write(strwriter.toString());

      writer.write(InterpreterUtil.getIndentString(indent));
      writer.write("}");
      writer.flush();
    }

    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASSNODE, nodeold);

    DecompilerContext.getLogger().endWriteClass();
  }

  public void classToJava(ClassNode node, BufferedWriter writer, int indent) throws IOException {

    ClassWrapper wrapper = node.wrapper;
    StructClass cl = wrapper.getClassStruct();

    ClassNode nodeold = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASSNODE, node);

    // last minute processing
    invokeProcessors(node);

    DecompilerContext.getLogger().startWriteClass(cl.qualifiedName);

    writeClassDefinition(node, writer, indent);

    // methods
    StringWriter strwriter = new StringWriter();
    BufferedWriter bufstrwriter = new BufferedWriter(strwriter);

    boolean firstmt = true;
    boolean mthidden = false;

    for (StructMethod mt : cl.getMethods()) {

      int flags = mt.getAccessFlags();

      boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || mt.getAttributes().containsKey("Synthetic");
      boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;

      if ((!isSynthetic || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC)) &&
          (!isBridge || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_BRIDGE)) &&
          !wrapper.getHideMembers().contains(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()))) {
        if (!mthidden && (!firstmt || node.type != ClassNode.CLASS_ANONYMOUS)) {
          bufstrwriter.write(DecompilerContext.getNewLineSeparator());
          firstmt = false;
        }

        mthidden = !methodToJava(node, mt, bufstrwriter, indent + 1);
      }
    }
    bufstrwriter.flush();

    StringWriter strwriter1 = new StringWriter();
    BufferedWriter bufstrwriter1 = new BufferedWriter(strwriter1);

    int fields_count = 0;

    boolean enumfields = false;

    // fields
    for (StructField fd : cl.getFields()) {
      int flags = fd.access_flags;
      boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || fd.getAttributes().containsKey("Synthetic");
      if ((!isSynthetic || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC))
          && !wrapper.getHideMembers().contains(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()))) {

        boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;
        if (isEnum) {
          if (enumfields) {
            bufstrwriter1.write(",");
            bufstrwriter1.write(DecompilerContext.getNewLineSeparator());
          }
          else {
            enumfields = true;
          }
        }
        else {
          if (enumfields) {
            bufstrwriter1.write(";");
            bufstrwriter1.write(DecompilerContext.getNewLineSeparator());
            enumfields = false;
          }
        }

        fieldToJava(wrapper, cl, fd, bufstrwriter1, indent + 1);
        fields_count++;
      }
    }

    if (enumfields) {
      bufstrwriter1.write(";");
      bufstrwriter1.write(DecompilerContext.getNewLineSeparator());
    }

    bufstrwriter1.flush();

    if (fields_count > 0) {
      writer.write(DecompilerContext.getNewLineSeparator());
      writer.write(strwriter1.toString());
      writer.write(DecompilerContext.getNewLineSeparator());
    }


    // methods
    writer.write(strwriter.toString());

    // member classes
    for (ClassNode inner : node.nested) {
      if (inner.type == ClassNode.CLASS_MEMBER) {
        StructClass innercl = inner.classStruct;

        boolean isSynthetic =
          ((inner.access | innercl.access_flags) & CodeConstants.ACC_SYNTHETIC) != 0 || innercl.getAttributes().containsKey("Synthetic");
        if ((!isSynthetic || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC))
            && !wrapper.getHideMembers().contains(innercl.qualifiedName)) {
          writer.write(DecompilerContext.getNewLineSeparator());
          classToJava(inner, writer, indent + 1);
        }
      }
    }

    writer.write(InterpreterUtil.getIndentString(indent));
    writer.write("}");
    if (node.type != ClassNode.CLASS_ANONYMOUS) {
      writer.write(DecompilerContext.getNewLineSeparator());
    }
    writer.flush();

    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASSNODE, nodeold);

    DecompilerContext.getLogger().endWriteClass();
  }

  private void writeClassDefinition(ClassNode node, BufferedWriter writer, int indent) throws IOException {

    if (node.type == ClassNode.CLASS_ANONYMOUS) {
      writer.write(" {");
      writer.write(DecompilerContext.getNewLineSeparator());
    }
    else {

      String indstr = InterpreterUtil.getIndentString(indent);

      ClassWrapper wrapper = node.wrapper;
      StructClass cl = wrapper.getClassStruct();

      int flags = node.type == ClassNode.CLASS_ROOT ? cl.access_flags : node.access;
      boolean isInterface = (flags & CodeConstants.ACC_INTERFACE) != 0;
      boolean isAnnotation = (flags & CodeConstants.ACC_ANNOTATION) != 0;
      boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;

      boolean isDeprecated = cl.getAttributes().containsKey("Deprecated");

      if (interceptor != null) {
        String oldname = interceptor.getOldName(cl.qualifiedName);
        if (oldname != null) {
          writer.write(indstr);
          writer.write("// $FF: renamed from: " + getDescriptorPrintOut(oldname, 0));
          writer.write(DecompilerContext.getNewLineSeparator());
        }
      }

      if (isDeprecated) {
        writer.write(indstr);
        writer.write("/** @deprecated */");
        writer.write(DecompilerContext.getNewLineSeparator());
      }

      // class annotations
      List<AnnotationExprent> lstAnn = getAllAnnotations(cl.getAttributes());
      for (AnnotationExprent annexpr : lstAnn) {
        writer.write(annexpr.toJava(indent));
        writer.write(DecompilerContext.getNewLineSeparator());
      }

      boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || cl.getAttributes().containsKey("Synthetic");

      if (isSynthetic) {
        writer.write(indstr);
        writer.write("// $FF: synthetic class");
        writer.write(DecompilerContext.getNewLineSeparator());
      }

      writer.write(indstr);

      if (isEnum) {
        // remove abstract and final flags (JLS 8.9 Enums)
        flags &= ~CodeConstants.ACC_ABSTRACT;
        flags &= ~CodeConstants.ACC_FINAL;
      }

      for (int i = 0; i < modval_class.length; i++) {
        if (!isInterface || !mod_notinterface.contains(modval_class[i])) {
          if ((flags & modval_class[i]) != 0) {
            writer.write(modstr_class[i]);
          }
        }
      }

      if (isEnum) {
        writer.write("enum ");
      }
      else if (isInterface) {
        if (isAnnotation) {
          writer.write("@");
        }
        writer.write("interface ");
      }
      else {
        writer.write("class ");
      }

      GenericClassDescriptor descriptor = null;
      if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
        StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)cl.getAttributes().getWithKey("Signature");
        if (attr != null) {
          descriptor = GenericMain.parseClassSignature(attr.getSignature());
        }
      }

      writer.write(node.simpleName);
      if (descriptor != null && !descriptor.fparameters.isEmpty()) {
        writer.write("<");
        for (int i = 0; i < descriptor.fparameters.size(); i++) {
          if (i > 0) {
            writer.write(", ");
          }
          writer.write(descriptor.fparameters.get(i));

          List<GenericType> lstBounds = descriptor.fbounds.get(i);
          if (lstBounds.size() > 1 || !"java/lang/Object".equals(lstBounds.get(0).value)) {
            writer.write(" extends ");
            writer.write(GenericMain.getGenericCastTypeName(lstBounds.get(0)));

            for (int j = 1; j < lstBounds.size(); j++) {
              writer.write(" & " + GenericMain.getGenericCastTypeName(lstBounds.get(j)));
            }
          }
        }
        writer.write(">");
      }
      writer.write(" ");

      if (!isEnum && !isInterface && cl.superClass != null) {
        VarType supertype = new VarType(cl.superClass.getString(), true);
        if (!VarType.VARTYPE_OBJECT.equals(supertype)) {
          writer.write("extends ");
          if (descriptor != null) {
            writer.write(GenericMain.getGenericCastTypeName(descriptor.superclass));
          }
          else {
            writer.write(ExprProcessor.getCastTypeName(supertype));
          }
          writer.write(" ");
        }
      }

      if (!isAnnotation) {
        int[] interfaces = cl.getInterfaces();
        if (interfaces.length > 0) {
          writer.write(isInterface ? "extends " : "implements ");
          for (int i = 0; i < interfaces.length; i++) {
            if (i > 0) {
              writer.write(", ");
            }
            if (descriptor != null) {
              writer.write(GenericMain.getGenericCastTypeName(descriptor.superinterfaces.get(i)));
            }
            else {
              writer.write(ExprProcessor.getCastTypeName(new VarType(cl.getInterface(i), true)));
            }
          }
          writer.write(" ");
        }
      }

      writer.write("{");
      writer.write(DecompilerContext.getNewLineSeparator());
    }
  }

  private void fieldToJava(ClassWrapper wrapper, StructClass cl, StructField fd, BufferedWriter writer, int indent) throws IOException {

    String indstr = InterpreterUtil.getIndentString(indent);

    boolean isInterface = (cl.access_flags & CodeConstants.ACC_INTERFACE) != 0;
    int flags = fd.access_flags;

    if (interceptor != null) {
      String oldname = interceptor.getOldName(cl.qualifiedName + " " + fd.getName() + " " + fd.getDescriptor());
      if (oldname != null) {
        String[] element = oldname.split(" ");

        writer.write(indstr);
        writer.write("// $FF: renamed from: " + element[1] + " " + getDescriptorPrintOut(element[2], 1));
        writer.write(DecompilerContext.getNewLineSeparator());
      }
    }

    boolean isDeprecated = fd.getAttributes().containsKey("Deprecated");

    if (isDeprecated) {
      writer.write(indstr);
      writer.write("/** @deprecated */");
      writer.write(DecompilerContext.getNewLineSeparator());
    }

    // field annotations
    List<AnnotationExprent> lstAnn = getAllAnnotations(fd.getAttributes());
    for (AnnotationExprent annexpr : lstAnn) {
      writer.write(annexpr.toJava(indent));
      writer.write(DecompilerContext.getNewLineSeparator());
    }

    boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || fd.getAttributes().containsKey("Synthetic");
    boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;

    if (isSynthetic) {
      writer.write(indstr);
      writer.write("// $FF: synthetic field");
      writer.write(DecompilerContext.getNewLineSeparator());
    }

    writer.write(indstr);

    if (!isEnum) {
      for (int i = 0; i < modval_field.length; i++) {
        if (!isInterface || !mod_notinterface_fields.contains(modval_field[i])) {
          if ((flags & modval_field[i]) != 0) {
            writer.write(modstr_field[i]);
          }
        }
      }
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
        writer.write(GenericMain.getGenericCastTypeName(descriptor.type));
      }
      else {
        writer.write(ExprProcessor.getCastTypeName(fieldType));
      }
      writer.write(" ");
    }

    writer.write(fd.getName());

    Exprent initializer;
    if ((flags & CodeConstants.ACC_STATIC) != 0) {
      initializer = wrapper.getStaticFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    }
    else {
      initializer = wrapper.getDynamicFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    }

    if (initializer != null) {
      if (isEnum && initializer.type == Exprent.EXPRENT_NEW) {
        NewExprent nexpr = (NewExprent)initializer;
        nexpr.setEnumconst(true);
        writer.write(nexpr.toJava(indent));
      }
      else {
        writer.write(" = ");
        writer.write(initializer.toJava(indent));
      }
    }
    else if ((flags & CodeConstants.ACC_FINAL) != 0 && (flags & CodeConstants.ACC_STATIC) != 0) {
      StructConstantValueAttribute attr =
        (StructConstantValueAttribute)fd.getAttributes().getWithKey(StructGeneralAttribute.ATTRIBUTE_CONSTANT_VALUE);
      if (attr != null) {
        PrimitiveConstant cnst = cl.getPool().getPrimitiveConstant(attr.getIndex());
        writer.write(" = ");
        writer.write(new ConstExprent(fieldType, cnst.value).toJava(indent));
      }
    }

    if (!isEnum) {
      writer.write(";");
      writer.write(DecompilerContext.getNewLineSeparator());
    }
  }

  public boolean methodLambdaToJava(ClassNode node_lambda,
                                    ClassNode node_content,
                                    StructMethod mt,
                                    BufferedWriter writer,
                                    int indent,
                                    boolean code_only) throws IOException {

    ClassWrapper wrapper = node_content.wrapper;

    MethodWrapper meth = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

    MethodWrapper methold = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, meth);

    String indstr = InterpreterUtil.getIndentString(indent);

    String method_name = node_lambda.lambda_information.method_name;
    MethodDescriptor md_content = MethodDescriptor.parseDescriptor(node_lambda.lambda_information.content_method_descriptor);
    MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(node_lambda.lambda_information.method_descriptor);

    StringWriter strwriter = new StringWriter();
    BufferedWriter bufstrwriter = new BufferedWriter(strwriter);

    if (!code_only) {
      bufstrwriter.write(indstr);
      bufstrwriter.write("public ");
      bufstrwriter.write(method_name);
      bufstrwriter.write("(");

      boolean firstpar = true;
      int index = node_lambda.lambda_information.is_content_method_static ? 0 : 1;
      ;

      int start_index = md_content.params.length - md_lambda.params.length;

      for (int i = 0; i < md_content.params.length; i++) {

        if (i >= start_index) {

          if (!firstpar) {
            bufstrwriter.write(", ");
          }

          VarType partype = md_content.params[i].copy();

          String strpartype = ExprProcessor.getCastTypeName(partype);
          if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(strpartype) &&
              DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
            strpartype = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
          }

          bufstrwriter.write(strpartype);
          bufstrwriter.write(" ");

          String parname = meth.varproc.getVarName(new VarVersionPaar(index, 0));
          bufstrwriter.write(parname == null ? "param" + index : parname); // null iff decompiled with errors

          firstpar = false;
        }

        index += md_content.params[i].stack_size;
      }

      bufstrwriter.write(")");
      bufstrwriter.write(" ");
      bufstrwriter.write("{");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }

    RootStatement root = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;

    if (root != null && !meth.decompiledWithErrors) { // check for existence
      try {
        String code = root.toJava(indent + 1);
        bufstrwriter.write(code);
      }
      catch (Throwable ex) {
        DecompilerContext.getLogger().writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " couldn't be written.", ex);
        meth.decompiledWithErrors = true;
      }
    }

    if (meth.decompiledWithErrors) {
      bufstrwriter.write(InterpreterUtil.getIndentString(indent + 1));
      bufstrwriter.write("// $FF: Couldn't be decompiled");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }

    if (!code_only) {
      bufstrwriter.write(indstr + "}");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }

    bufstrwriter.flush();

    writer.write(strwriter.toString());

    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methold);

    return true;
  }

  public boolean methodToJava(ClassNode node, StructMethod mt, BufferedWriter writer, int indent) throws IOException {

    ClassWrapper wrapper = node.wrapper;
    StructClass cl = wrapper.getClassStruct();

    MethodWrapper meth = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

    MethodWrapper methold = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, meth);

    boolean isInterface = (cl.access_flags & CodeConstants.ACC_INTERFACE) != 0;
    boolean isAnnotation = (cl.access_flags & CodeConstants.ACC_ANNOTATION) != 0;
    boolean isEnum = (cl.access_flags & CodeConstants.ACC_ENUM) != 0 && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
    boolean isDeprecated = mt.getAttributes().containsKey("Deprecated");

    String indstr = InterpreterUtil.getIndentString(indent);
    boolean clinit = false, init = false, dinit = false;

    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

    StringWriter strwriter = new StringWriter();
    BufferedWriter bufstrwriter = new BufferedWriter(strwriter);

    int flags = mt.getAccessFlags();
    if ((flags & CodeConstants.ACC_NATIVE) != 0) {
      flags &= ~CodeConstants.ACC_STRICT; // compiler bug: a strictfp class sets all methods to strictfp
    }

    if ("<clinit>".equals(mt.getName())) {
      flags &= CodeConstants.ACC_STATIC; // ingnore all modifiers except 'static' in a static initializer
    }

    if (interceptor != null) {
      String oldname = interceptor.getOldName(cl.qualifiedName + " " + mt.getName() + " " + mt.getDescriptor());
      if (oldname != null) {
        String[] element = oldname.split(" ");

        bufstrwriter.write(indstr);
        bufstrwriter.write("// $FF: renamed from: " + element[1] + " " + getDescriptorPrintOut(element[2], 2));
        bufstrwriter.write(DecompilerContext.getNewLineSeparator());
      }
    }

    if (isDeprecated) {
      writer.write(indstr);
      writer.write("/** @deprecated */");
      writer.write(DecompilerContext.getNewLineSeparator());
    }

    // method annotations
    List<AnnotationExprent> lstAnn = getAllAnnotations(mt.getAttributes());
    for (AnnotationExprent annexpr : lstAnn) {
      bufstrwriter.write(annexpr.toJava(indent));
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }

    boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || mt.getAttributes().containsKey("Synthetic");
    boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;

    if (isSynthetic) {
      bufstrwriter.write(indstr);
      bufstrwriter.write("// $FF: synthetic method");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }

    if (isBridge) {
      bufstrwriter.write(indstr);
      bufstrwriter.write("// $FF: bridge method");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }

    bufstrwriter.write(indstr);
    for (int i = 0; i < modval_meth.length; i++) {
      if (!isInterface || !mod_notinterface_meth.contains(modval_meth[i])) {
        if ((flags & modval_meth[i]) != 0) {
          bufstrwriter.write(modstr_meth[i]);
        }
      }
    }

    // 'default' modifier (Java 8)
    if (isInterface && mt.containsCode()) {
      bufstrwriter.write("default ");
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
        int actualParams = md.params.length;
        if (isEnum && init) actualParams -= 2;
        if (actualParams != descriptor.params.size()) {
          DecompilerContext.getLogger()
            .writeMessage("Inconsistent generic signature in method " + mt.getName() + " " + mt.getDescriptor(), IFernflowerLogger.WARNING);
          descriptor = null;
        }
      }
    }

    boolean throwsExceptions = false;

    int param_count_explicit = 0;

    if (!clinit && !dinit) {

      boolean thisvar = (mt.getAccessFlags() & CodeConstants.ACC_STATIC) == 0;

      // formal type parameters
      if (descriptor != null && !descriptor.fparameters.isEmpty()) {
        bufstrwriter.write("<");
        for (int i = 0; i < descriptor.fparameters.size(); i++) {
          if (i > 0) {
            bufstrwriter.write(", ");
          }
          bufstrwriter.write(descriptor.fparameters.get(i));

          List<GenericType> lstBounds = descriptor.fbounds.get(i);
          if (lstBounds.size() > 1 || !"java/lang/Object".equals(lstBounds.get(0).value)) {
            bufstrwriter.write(" extends ");
            bufstrwriter.write(GenericMain.getGenericCastTypeName(lstBounds.get(0)));

            for (int j = 1; j < lstBounds.size(); j++) {
              bufstrwriter.write(" & " + GenericMain.getGenericCastTypeName(lstBounds.get(j)));
            }
          }
        }
        bufstrwriter.write("> ");
      }

      if (!init) {
        if (descriptor != null) {
          bufstrwriter.write(GenericMain.getGenericCastTypeName(descriptor.ret));
        }
        else {
          bufstrwriter.write(ExprProcessor.getCastTypeName(md.ret));
        }
        bufstrwriter.write(" ");
      }

      bufstrwriter.write(name);
      bufstrwriter.write("(");

      // parameter annotations
      List<List<AnnotationExprent>> lstParAnn = getAllParameterAnnotations(mt.getAttributes());

      List<VarVersionPaar> signFields = meth.signatureFields;

      // compute last visible parameter
      int lastparam_index = -1;
      for (int i = 0; i < md.params.length; i++) {
        if (signFields == null || signFields.get(i) == null) {
          lastparam_index = i;
        }
      }

      boolean firstpar = true;
      int index = isEnum && init ? 3 : thisvar ? 1 : 0;
      int start = isEnum && init && descriptor == null ? 2 : 0;
      int params = descriptor == null ? md.params.length : descriptor.params.size();
      for (int i = start; i < params; i++) {
        if (signFields == null || signFields.get(i) == null) {

          if (!firstpar) {
            bufstrwriter.write(", ");
          }

          if (lstParAnn.size() > param_count_explicit) {
            List<AnnotationExprent> annotations = lstParAnn.get(param_count_explicit);
            for (int j = 0; j < annotations.size(); j++) {
              AnnotationExprent annexpr = annotations.get(j);
              if (annexpr.getAnnotationType() == AnnotationExprent.ANNOTATION_NORMAL) {
                bufstrwriter.write(DecompilerContext.getNewLineSeparator());
                bufstrwriter.write(annexpr.toJava(indent + 1));
              }
              else {
                bufstrwriter.write(annexpr.toJava(0));
              }
              bufstrwriter.write(" ");
            }
          }

          if (meth.varproc.getVarFinal(new VarVersionPaar(index, 0)) == VarTypeProcessor.VAR_FINALEXPLICIT) {
            bufstrwriter.write("final ");
          }


          if (descriptor != null) {
            GenericType partype = descriptor.params.get(i);

            boolean isVarArgs = (i == lastparam_index && (mt.getAccessFlags() & CodeConstants.ACC_VARARGS) != 0
                                 && partype.arraydim > 0);

            if (isVarArgs) {
              partype.arraydim--;
            }

            String strpartype = GenericMain.getGenericCastTypeName(partype);
            if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(strpartype) &&
                DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
              strpartype = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
            }

            bufstrwriter.write(strpartype);

            if (isVarArgs) {
              bufstrwriter.write(" ...");
            }
          }
          else {
            VarType partype = md.params[i].copy();

            boolean isVarArgs = (i == lastparam_index && (mt.getAccessFlags() & CodeConstants.ACC_VARARGS) != 0
                                 && partype.arraydim > 0);

            if (isVarArgs) {
              partype.decArrayDim();
            }

            String strpartype = ExprProcessor.getCastTypeName(partype);
            if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(strpartype) &&
                DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
              strpartype = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
            }

            bufstrwriter.write(strpartype);

            if (isVarArgs) {
              bufstrwriter.write(" ...");
            }
          }

          bufstrwriter.write(" ");
          String parname = meth.varproc.getVarName(new VarVersionPaar(index, 0));
          bufstrwriter.write(parname == null ? "param" + index : parname); // null iff decompiled with errors
          firstpar = false;
          param_count_explicit++;
        }

        index += md.params[i].stack_size;
      }

      bufstrwriter.write(")");

      StructExceptionsAttribute attr = (StructExceptionsAttribute)mt.getAttributes().getWithKey("Exceptions");
      if ((descriptor != null && !descriptor.exceptions.isEmpty()) || attr != null) {
        throwsExceptions = true;
        bufstrwriter.write(" throws ");

        for (int i = 0; i < attr.getThrowsExceptions().size(); i++) {
          if (i > 0) {
            bufstrwriter.write(", ");
          }
          if (descriptor != null && !descriptor.exceptions.isEmpty()) {
            bufstrwriter.write(GenericMain.getGenericCastTypeName(descriptor.exceptions.get(i)));
          }
          else {
            VarType exctype = new VarType(attr.getExcClassname(i, cl.getPool()), true);
            bufstrwriter.write(ExprProcessor.getCastTypeName(exctype));
          }
        }
      }
    }

    boolean hidemethod = false;

    if ((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) { // native or abstract method (explicit or interface)
      if (isAnnotation) {
        StructAnnDefaultAttribute attr = (StructAnnDefaultAttribute)mt.getAttributes().getWithKey("AnnotationDefault");
        if (attr != null) {
          bufstrwriter.write(" default ");
          bufstrwriter.write(attr.getDefaultValue().toJava(indent + 1));
        }
      }

      bufstrwriter.write(";");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }
    else {
      if (!clinit && !dinit) {
        bufstrwriter.write(" ");
      }
      bufstrwriter.write("{");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());

      RootStatement root = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;

      if (root != null && !meth.decompiledWithErrors) { // check for existence
        try {
          String code = root.toJava(indent + 1);

          boolean singleinit = false;
          if (init &&
              param_count_explicit == 0 &&
              !throwsExceptions &&
              DecompilerContext.getOption(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR)) {
            int init_counter = 0;
            for (MethodWrapper mth : wrapper.getMethods()) {
              if ("<init>".equals(mth.methodStruct.getName())) {
                init_counter++;
              }
            }
            singleinit = (init_counter == 1);
          }

          hidemethod = (clinit || dinit || singleinit) && code.length() == 0;

          bufstrwriter.write(code);
        }
        catch (Throwable ex) {
          DecompilerContext.getLogger().writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " couldn't be written.", ex);
          meth.decompiledWithErrors = true;
        }
      }

      if (meth.decompiledWithErrors) {
        bufstrwriter.write(InterpreterUtil.getIndentString(indent + 1));
        bufstrwriter.write("// $FF: Couldn't be decompiled");
        bufstrwriter.write(DecompilerContext.getNewLineSeparator());
      }

      bufstrwriter.write(indstr + "}");
      bufstrwriter.write(DecompilerContext.getNewLineSeparator());
    }

    bufstrwriter.flush();

    if (!hidemethod) {
      writer.write(strwriter.toString());
    }

    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methold);

    return !hidemethod;
  }

  private List<AnnotationExprent> getAllAnnotations(VBStyleCollection<StructGeneralAttribute, String> attributes) {

    String[] annattrnames = new String[]{StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS,
      StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS};

    List<AnnotationExprent> lst = new ArrayList<AnnotationExprent>();

    for (String attrname : annattrnames) {
      StructAnnotationAttribute attr = (StructAnnotationAttribute)attributes.getWithKey(attrname);
      if (attr != null) {
        lst.addAll(attr.getAnnotations());
      }
    }

    return lst;
  }

  private List<List<AnnotationExprent>> getAllParameterAnnotations(VBStyleCollection<StructGeneralAttribute, String> attributes) {

    String[] annattrnames = new String[]{StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS,
      StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS};

    List<List<AnnotationExprent>> ret = new ArrayList<List<AnnotationExprent>>();

    for (String attrname : annattrnames) {
      StructAnnotationParameterAttribute attr = (StructAnnotationParameterAttribute)attributes.getWithKey(attrname);
      if (attr != null) {
        for (int i = 0; i < attr.getParamAnnotations().size(); i++) {
          List<AnnotationExprent> lst = new ArrayList<AnnotationExprent>();
          boolean isnew = (ret.size() <= i);

          if (!isnew) {
            lst = ret.get(i);
          }
          lst.addAll(attr.getParamAnnotations().get(i));

          if (isnew) {
            ret.add(lst);
          }
          else {
            ret.set(i, lst);
          }
        }
      }
    }

    return ret;
  }

  private String getDescriptorPrintOut(String descriptor, int element) {

    switch (element) {
      case 0: // class
        return ExprProcessor.buildJavaClassName(descriptor);
      case 1: // field
        return getTypePrintOut(FieldDescriptor.parseDescriptor(descriptor).type);
      case 2: // method
      default:
        MethodDescriptor md = MethodDescriptor.parseDescriptor(descriptor);

        StringBuilder buffer = new StringBuilder("(");

        boolean first = true;
        for (VarType partype : md.params) {
          if (first) {
            first = false;
          }
          else {
            buffer.append(", ");
          }
          buffer.append(getTypePrintOut(partype));
        }
        buffer.append(") ");
        buffer.append(getTypePrintOut(md.ret));

        return buffer.toString();
    }
  }

  private String getTypePrintOut(VarType type) {
    String strtype = ExprProcessor.getCastTypeName(type, false);
    if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(strtype) &&
        DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
      strtype = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT, false);
    }
    return strtype;
  }
}
