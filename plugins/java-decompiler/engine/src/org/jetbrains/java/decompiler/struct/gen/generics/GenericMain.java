// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypeAnnotationWriteHelper;
import org.jetbrains.java.decompiler.struct.StructTypePathEntry;

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

  public static String getGenericCastTypeName(GenericType type, List<TypeAnnotationWriteHelper> typePathWriteStack) {
    List<TypeAnnotationWriteHelper> arrayPaths = new ArrayList<>();
    List<TypeAnnotationWriteHelper> notArrayPath = typePathWriteStack.stream().filter(stack -> {
      boolean isArrayPath = stack.getPaths().size() < type.arrayDim;
      if (stack.getPaths().size() > type.arrayDim) {
        for (int i = 0; i < type.arrayDim; i++) {
          stack.getPaths().poll(); // remove all trailing
        }
      }
      if (isArrayPath) {
        arrayPaths.add(stack);
      }
      return !isArrayPath;
    }).collect(Collectors.toList());
    StringBuilder sb = new StringBuilder(getTypeName(type, notArrayPath));
    ExprProcessor.writeArray(sb, type.arrayDim, arrayPaths);
    return sb.toString();
  }

  private static String getTypeName(GenericType type, List<TypeAnnotationWriteHelper> typePathWriteStack) {
    int tp = type.type;
    if (tp <= CodeConstants.TYPE_BOOLEAN) {
      return typeNames[tp];
    }
    else if (tp == CodeConstants.TYPE_VOID) {
      return "void";
    }
    else if (tp == CodeConstants.TYPE_GENVAR) {
      StringBuilder sb = new StringBuilder();
      appendTypeAnnotationBeforeType(type, sb, typePathWriteStack);
      sb.append(type.value);
      return sb.toString();
    }
    else if (tp == CodeConstants.TYPE_OBJECT) {
      StringBuilder sb = new StringBuilder();
      appendClassName(type, sb, typePathWriteStack);
      return sb.toString();
    }

    throw new RuntimeException("Invalid type: " + type);
  }

  private static void appendClassName(GenericType type, StringBuilder sb, List<TypeAnnotationWriteHelper> typePathWriteStack) {
    List<GenericType> enclosingClasses = type.getEnclosingClasses();

    appendTypeAnnotationBeforeType(type, sb, typePathWriteStack);

    if (enclosingClasses.isEmpty()) {
      String name = type.value.replace('/', '.');
      sb.append(DecompilerContext.getImportCollector().getNestedName(name));
    }
    else {
      for (GenericType tp : enclosingClasses) {
        String[] nestedClasses = DecompilerContext.getImportCollector().getNestedName(tp.value.replace('/', '.')).split("\\.");
        for (int i = 0; i < nestedClasses.length; i++) {
          String nestedType = nestedClasses[i];
          if (i != 0) { // first annotation is written already
            ExprProcessor.checkNestedTypeAnnotation(sb, typePathWriteStack);
          }

          sb.append(nestedType);
          if (i != nestedClasses.length - 1) sb.append(".");
        }
        appendTypeArguments(tp, sb, typePathWriteStack);
        sb.append('.');
        ExprProcessor.checkNestedTypeAnnotation(sb, typePathWriteStack);
      }

      sb.append(type.value);
    }

    appendTypeArguments(type, sb, typePathWriteStack);
  }

  private static void appendTypeAnnotationBeforeType(GenericType type, StringBuilder sb, List<TypeAnnotationWriteHelper> typePathWriteStack) {
    typePathWriteStack.removeIf(writeHelper -> {
      StructTypePathEntry path = writeHelper.getPaths().peek();
      if (path == null) {
        writeHelper.writeTo(sb);
        return true;
      }
      if (path.getTypePathEntryKind() == StructTypePathEntry.Kind.ARRAY.getOpcode() && type.arrayDim == writeHelper.getPaths().size()) {
        writeHelper.writeTo(sb);
        return true;
      }
      return false;
    });
  }

  private static void appendTypeArguments(GenericType type, StringBuilder sb, List<TypeAnnotationWriteHelper> typePathWriteStack) {
    if (!type.getArguments().isEmpty()) {
      sb.append('<');

      for (int i = 0; i < type.getArguments().size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }

        GenericType genPar = type.getArguments().get(i);
        int wildcard = type.getWildcards().get(i);
        final int it = i;

        // only take type paths that are in the generic
        List<TypeAnnotationWriteHelper> locTypePathWriteStack = typePathWriteStack.stream().filter(writeHelper -> {
          StructTypePathEntry path = writeHelper.getPaths().peek();
          boolean inGeneric = path != null && path.getTypeArgumentIndex() == it && path.getTypePathEntryKind() == StructTypePathEntry.Kind.TYPE.getOpcode();
          if (inGeneric) {
            writeHelper.getPaths().pop();
          }
          return inGeneric;
        }).collect(Collectors.toList());

        locTypePathWriteStack.removeIf(writeHelper -> {
          StructTypePathEntry path = writeHelper.getPaths().peek();
          if (path == null && wildcard != GenericType.WILDCARD_NO) {
            writeHelper.writeTo(sb);
            return true;
          }
          if (path != null && path.getTypePathEntryKind() == StructTypePathEntry.Kind.TYPE.getOpcode() && path.getTypeArgumentIndex() == it &&
            genPar.arrayDim != 0 && genPar.arrayDim == writeHelper.getPaths().size()
          ) {
            writeHelper.writeTo(sb);
            return true;
          }
          return false;
        });

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


        typePathWriteStack.forEach(writeHelper -> { // remove all wild card entries
          StructTypePathEntry path = writeHelper.getPaths().peek();
          boolean isWildCard = path != null && path.getTypePathEntryKind() == StructTypePathEntry.Kind.TYPE_WILDCARD.getOpcode();
          if (isWildCard && path.getTypeArgumentIndex() == it) writeHelper.getPaths().pop();
        });
        locTypePathWriteStack.removeIf(writeHelper -> {
          StructTypePathEntry path = writeHelper.getPaths().peek();
          if (path != null && path.getTypeArgumentIndex() == it &&
              path.getTypePathEntryKind() == StructTypePathEntry.Kind.TYPE_WILDCARD.getOpcode() &&
            writeHelper.getPaths().size() - 1 == genPar.arrayDim
          ) {
            writeHelper.writeTo(sb);
            return true;
          }
          return false;
        });

        if (genPar != null) {
          sb.append(getGenericCastTypeName(genPar, locTypePathWriteStack));
        }
      }

      sb.append(">");
    }
  }
}
