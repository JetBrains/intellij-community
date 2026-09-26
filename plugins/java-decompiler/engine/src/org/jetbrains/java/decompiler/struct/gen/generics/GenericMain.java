// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypeAnnotationWriteHelper;
import org.jetbrains.java.decompiler.struct.StructTypePathEntry;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
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

  public static GenericClassDescriptor parseClassSignature(String qualifiedName, String signature) {
    String original = signature;
    try {
      GenericClassDescriptor descriptor = new GenericClassDescriptor();

      signature = parseFormalParameters(signature, descriptor.fparameters, descriptor.fbounds);

      String superCl = GenericType.getNextType(signature);
      descriptor.superclass = GenericType.parse(superCl);

      signature = signature.substring(superCl.length());
      while (!signature.isEmpty()) {
        String superIf = GenericType.getNextType(signature);
        descriptor.superinterfaces.add(GenericType.parse(superIf));
        signature = signature.substring(superIf.length());
      }

      StringBuilder buf = new StringBuilder();
      buf.append('L').append(qualifiedName).append('<');
      for (String t : descriptor.fparameters) {
        buf.append('T').append(t).append(';');
      }
      buf.append(">;");
      descriptor.genericType = (GenericType)GenericType.parse(buf.toString());

      return descriptor;
    }
    catch (RuntimeException e) {
      DecompilerContext.getLogger().writeMessage("Invalid signature: " + original, IFernflowerLogger.Severity.WARN);
      return null;
    }
  }

  public static GenericFieldDescriptor parseFieldSignature(String signature) {
    try {
      return new GenericFieldDescriptor(GenericType.parse(signature));
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
      List<List<VarType>> typeParameterBounds = new ArrayList<>();
      signature = parseFormalParameters(signature, typeParameters, typeParameterBounds);

      int to = signature.indexOf(")");
      String parameters = signature.substring(1, to);
      signature = signature.substring(to + 1);

      List<VarType> parameterTypes = new ArrayList<>();
      while (!parameters.isEmpty()) {
        String par = GenericType.getNextType(parameters);
        parameterTypes.add(GenericType.parse(par));
        parameters = parameters.substring(par.length());
      }

      String ret = GenericType.getNextType(signature);
      VarType returnType = GenericType.parse(ret);
      signature = signature.substring(ret.length());

      List<VarType> exceptionTypes = new ArrayList<>();
      if (!signature.isEmpty()) {
        String[] exceptions = signature.split("\\^");
        for (int i = 1; i < exceptions.length; i++) {
          exceptionTypes.add(GenericType.parse(exceptions[i]));
        }
      }

      return new GenericMethodDescriptor(typeParameters, typeParameterBounds, parameterTypes, returnType, exceptionTypes);
    }
    catch (RuntimeException e) {
      DecompilerContext.getLogger().writeMessage("Invalid signature: " + original, IFernflowerLogger.Severity.WARN);
      return null;
    }
  }

  private static String parseFormalParameters(String signature, List<String> parameters, List<List<VarType>> bounds) {
    if (signature.charAt(0) != '<') {
      return signature;
    }

    int counter = 1;
    int index = 1;

    loop:
    while (index < signature.length()) {
      switch (signature.charAt(index)) {
        case '<' -> counter++;
        case '>' -> {
          counter--;
          if (counter == 0) {
            break loop;
          }
        }
      }

      index++;
    }

    String value = signature.substring(1, index);
    signature = signature.substring(index + 1);

    while (!value.isEmpty()) {
      int to = value.indexOf(":");

      String param = value.substring(0, to);
      value = value.substring(to + 1);

      List<VarType> lstBounds = new ArrayList<>();

      while (true) {
        if (value.charAt(0) == ':') {
          // empty superclass, skip
          value = value.substring(1);
        }

        String bound = GenericType.getNextType(value);
        lstBounds.add(GenericType.parse(bound));
        value = value.substring(bound.length());


        if (value.isEmpty() || value.charAt(0) != ':') {
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
      type.appendCastName(sb, typeAnnWriteHelpers);
      return sb.toString();
    }

    throw new RuntimeException("Invalid type: " + type);
  }

  public static List<TypeAnnotationWriteHelper> getGenericTypeAnnotations(
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

  public static List<TypeAnnotationWriteHelper> writeTypeAnnotationBeforeWildCard(
    StringBuilder sb,
    VarType type,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    return typeAnnWriteHelpers.stream().filter(typeAnnWriteHelper -> {
      StructTypePathEntry path = typeAnnWriteHelper.getPaths().peek();
      if ((type instanceof GenericType genericType && genericType.getWildcard() != GenericType.WILDCARD_NO ||
           type == null) && path == null) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      if (type != null && type.getArrayDim() == typeAnnWriteHelper.getPaths().size() && type.getArrayDim() == typeAnnWriteHelper.arrayPathCount()) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      return true;
    }).collect(Collectors.toList());
  }

  public static List<TypeAnnotationWriteHelper> writeTypeAnnotationAfterWildCard(
    StringBuilder sb,
    VarType type,
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
