// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypeAnnotationWriteHelper;
import org.jetbrains.java.decompiler.struct.StructTypePathEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class GenericMain {

  private static final String[] typeNames = {
    "byte",
    "char",
    "double",
    "float",
    "int",
    "long",
    "short",
    "boolean",
  };

  public static GenericClassDescriptor parseClassSignature(String signature) {
    String original = signature;
    try {
      GenericClassDescriptor descriptor = new GenericClassDescriptor();

      signature = parseFormalParameters(signature, descriptor.fparameters, descriptor.fbounds);

      String superCl = GenericType.getNextType(signature);
      descriptor.superclass = new GenericType(superCl);

      signature = signature.substring(superCl.length());
      while (signature.length() > 0) {
        String superIf = GenericType.getNextType(signature);
        descriptor.superinterfaces.add(new GenericType(superIf));
        signature = signature.substring(superIf.length());
      }

      return descriptor;
    }
    catch (RuntimeException e) {
      DecompilerContext.getLogger().writeMessage("Invalid signature: " + original, IFernflowerLogger.Severity.WARN);
      return null;
    }
  }

  public static GenericFieldDescriptor parseFieldSignature(String signature) {
    try {
      return new GenericFieldDescriptor(new GenericType(signature));
    }
    catch (RuntimeException e) {
      DecompilerContext.getLogger().writeMessage("Invalid signature: " + signature, IFernflowerLogger.Severity.WARN);
      return null;
    }
  }

  public static GenericMethodDescriptor parseMethodSignature(String signature) {
    String original = signature;
    try {
      List<String> typeParameters = new ArrayList<>();
      List<List<GenericType>> typeParameterBounds = new ArrayList<>();
      signature = parseFormalParameters(signature, typeParameters, typeParameterBounds);

      int to = signature.indexOf(")");
      String parameters = signature.substring(1, to);
      signature = signature.substring(to + 1);

      List<GenericType> parameterTypes = new ArrayList<>();
      while (parameters.length() > 0) {
        String par = GenericType.getNextType(parameters);
        parameterTypes.add(new GenericType(par));
        parameters = parameters.substring(par.length());
      }

      String ret = GenericType.getNextType(signature);
      GenericType returnType = new GenericType(ret);
      signature = signature.substring(ret.length());

      List<GenericType> exceptionTypes = new ArrayList<>();
      if (signature.length() > 0) {
        String[] exceptions = signature.split("\\^");
        for (int i = 1; i < exceptions.length; i++) {
          exceptionTypes.add(new GenericType(exceptions[i]));
        }
      }

      return new GenericMethodDescriptor(typeParameters, typeParameterBounds, parameterTypes, returnType, exceptionTypes);
    }
    catch (RuntimeException e) {
      DecompilerContext.getLogger().writeMessage("Invalid signature: " + original, IFernflowerLogger.Severity.WARN);
      return null;
    }
  }

  private static String parseFormalParameters(String signature, List<? super String> parameters, List<? super List<GenericType>> bounds) {
    if (signature.charAt(0) != '<') {
      return signature;
    }

    int counter = 1;
    int index = 1;

    loop:
    while (index < signature.length()) {
      switch (signature.charAt(index)) {
        case '<':
          counter++;
          break;
        case '>':
          counter--;
          if (counter == 0) {
            break loop;
          }
      }

      index++;
    }

    String value = signature.substring(1, index);
    signature = signature.substring(index + 1);

    while (value.length() > 0) {
      int to = value.indexOf(":");

      String param = value.substring(0, to);
      value = value.substring(to + 1);

      List<GenericType> lstBounds = new ArrayList<>();

      while (true) {
        if (value.charAt(0) == ':') {
          // empty superclass, skip
          value = value.substring(1);
        }

        String bound = GenericType.getNextType(value);
        lstBounds.add(new GenericType(bound));
        value = value.substring(bound.length());


        if (value.length() == 0 || value.charAt(0) != ':') {
          break;
        }
        else {
          value = value.substring(1);
        }
      }

      parameters.add(param);
      bounds.add(lstBounds);
    }

    return signature;
  }

  public static String getGenericCastTypeName(GenericType type, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    List<TypeAnnotationWriteHelper> arrayTypeAnnWriteHelpers = ExprProcessor.arrayPath(type, typeAnnWriteHelpers);
    List<TypeAnnotationWriteHelper> nonArrayTypeAnnWriteHelpers = ExprProcessor.nonArrayPath(type, typeAnnWriteHelpers);
    StringBuilder sb = new StringBuilder(getTypeName(type, nonArrayTypeAnnWriteHelpers));
    ExprProcessor.writeArray(sb, type.getArrayDim(), arrayTypeAnnWriteHelpers);
    return sb.toString();
  }

  private static String getTypeName(GenericType type, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    int tp = type.getType();
    if (tp <= CodeConstants.TYPE_BOOLEAN) {
      return typeNames[tp];
    }
    else if (tp == CodeConstants.TYPE_VOID) {
      return "void";
    }
    else if (tp == CodeConstants.TYPE_GENVAR) {
      StringBuilder sb = new StringBuilder();
      ExprProcessor.writeTypeAnnotationBeforeType(type, sb, typeAnnWriteHelpers);
      sb.append(type.getValue());
      return sb.toString();
    }
    else if (tp == CodeConstants.TYPE_OBJECT) {
      StringBuilder sb = new StringBuilder();
      appendClassName(type, sb, typeAnnWriteHelpers);
      return sb.toString();
    }

    throw new RuntimeException("Invalid type: " + type);
  }

  private static void appendClassName(GenericType type, StringBuilder sb, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    List<GenericType> enclosingTypes = type.getEnclosingClasses();
    typeAnnWriteHelpers = ExprProcessor.writeTypeAnnotationBeforeType(type, sb, typeAnnWriteHelpers);
    if (enclosingTypes.isEmpty()) {
      List<String> nestedTypes = Arrays.asList(
        DecompilerContext.getImportCollector().getNestedName(type.getValue().replace('/', '.')).split("\\.")
      );
      ExprProcessor.writeNestedClass(sb, type, nestedTypes, typeAnnWriteHelpers);
      ExprProcessor.popNestedTypeAnnotation(typeAnnWriteHelpers);
    }
    else {
      for (GenericType tp : enclosingTypes) {
        List<String> nestedTypes = Arrays.asList(
          DecompilerContext.getImportCollector().getNestedName(tp.getValue().replace('/', '.')).split("\\.")
        );
        typeAnnWriteHelpers = ExprProcessor.writeNestedClass(sb, type, nestedTypes, typeAnnWriteHelpers);
        typeAnnWriteHelpers = appendTypeArguments(tp, sb, typeAnnWriteHelpers);
        ExprProcessor.popNestedTypeAnnotation(typeAnnWriteHelpers);
        sb.append('.');
      }
      typeAnnWriteHelpers = ExprProcessor.writeNestedTypeAnnotations(sb, typeAnnWriteHelpers);
      ExprProcessor.popNestedTypeAnnotation(typeAnnWriteHelpers);
      sb.append(type.getValue());
    }
    appendTypeArguments(type, sb, typeAnnWriteHelpers);
  }

  private static List<TypeAnnotationWriteHelper> appendTypeArguments(
    GenericType type,
    StringBuilder sb,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    if (!type.getArguments().isEmpty()) {
      sb.append('<');

      for (int i = 0; i < type.getArguments().size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }

        GenericType genPar = type.getArguments().get(i);
        int wildcard = type.getWildcards().get(i);

        // only take type paths that are in the generic
        List<TypeAnnotationWriteHelper> locTypeAnnWriteHelpers = getGenericTypeAnnotations(i, typeAnnWriteHelpers);
        typeAnnWriteHelpers.removeAll(locTypeAnnWriteHelpers);
        locTypeAnnWriteHelpers = writeTypeAnnotationBeforeWildCard(sb, genPar, wildcard, locTypeAnnWriteHelpers);
        switch (wildcard) {
          case GenericType.WILDCARD_UNBOUND:
            sb.append('?');
            break;
          case GenericType.WILDCARD_EXTENDS:
            sb.append("? extends ");
            break;
          case GenericType.WILDCARD_SUPER:
            sb.append("? super ");
            break;
        }
        locTypeAnnWriteHelpers = writeTypeAnnotationAfterWildCard(sb, genPar, locTypeAnnWriteHelpers);
        if (genPar != null) {
          sb.append(getGenericCastTypeName(genPar, locTypeAnnWriteHelpers));
        }
      }

      sb.append(">");
    }
    return typeAnnWriteHelpers;
  }

  private static List<TypeAnnotationWriteHelper> getGenericTypeAnnotations(
    int argIndex,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    return typeAnnWriteHelpers.stream().filter(typeAnnWriteHelper -> {
      StructTypePathEntry entry = typeAnnWriteHelper.getPaths().peek();
      boolean inGeneric = entry != null && entry.getTypeArgumentIndex() == argIndex
                          && entry.getTypePathEntryKind() == StructTypePathEntry.Kind.TYPE.getId();
      if (inGeneric) typeAnnWriteHelper.getPaths().pop();
      return inGeneric;
    }).collect(Collectors.toList());
  }

  private static List<TypeAnnotationWriteHelper> writeTypeAnnotationBeforeWildCard(
    StringBuilder sb,
    GenericType type,
    int wildcard,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    return typeAnnWriteHelpers.stream().filter(typeAnnWriteHelper -> {
      StructTypePathEntry path = typeAnnWriteHelper.getPaths().peek();
      if (wildcard != GenericType.WILDCARD_NO && path == null) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      if (type.getArrayDim() == typeAnnWriteHelper.getPaths().size() && type.getArrayDim() == typeAnnWriteHelper.arrayPathCount()) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      return true;
    }).collect(Collectors.toList());
  }

  private static List<TypeAnnotationWriteHelper> writeTypeAnnotationAfterWildCard(
    StringBuilder sb,
    GenericType type,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    typeAnnWriteHelpers.forEach(typeAnnWriteHelper -> { // remove all wild card path entries
      StructTypePathEntry path = typeAnnWriteHelper.getPaths().peek();
      boolean isWildCard = path != null && path.getTypePathEntryKind() == StructTypePathEntry.Kind.TYPE_WILDCARD.getId();
      if (isWildCard) typeAnnWriteHelper.getPaths().pop();
    });
    return typeAnnWriteHelpers.stream().filter(typeAnnWriteHelper -> {
      StructTypePathEntry path = typeAnnWriteHelper.getPaths().peek();
      if (type.getArrayDim() == 0 && path == null) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      if (type.getArrayDim() == typeAnnWriteHelper.getPaths().size() && type.getArrayDim() == typeAnnWriteHelper.arrayPathCount()) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      return true;
    }).collect(Collectors.toList());
  }
}
