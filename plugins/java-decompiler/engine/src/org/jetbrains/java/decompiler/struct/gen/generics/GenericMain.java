// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypePathWriteProgress;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.StructTypePath;

import java.util.*;
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

  public static String getGenericCastTypeName(GenericType type, List<TypePathWriteProgress> typePathWriteStack) {
    List<TypePathWriteProgress> arrayPaths = new ArrayList<>();
    List<TypePathWriteProgress> notArrayPath = typePathWriteStack.stream().filter(stack -> {
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

  private static String getTypeName(GenericType type, List<TypePathWriteProgress> typePathWriteStack) {
    int tp = type.type;
    if (tp <= CodeConstants.TYPE_BOOLEAN) {
      return typeNames[tp];
    }
    else if (tp == CodeConstants.TYPE_VOID) {
      return "void";
    }
    else if (tp == CodeConstants.TYPE_GENVAR) {
      return type.value;
    }
    else if (tp == CodeConstants.TYPE_OBJECT) {
      StringBuilder buffer = new StringBuilder();
      appendClassName(type, buffer, typePathWriteStack);
      return buffer.toString();
    }

    throw new RuntimeException("Invalid type: " + type);
  }

  private static void appendClassName(GenericType type, StringBuilder sb, List<TypePathWriteProgress> typePathWriteStack) {
    List<GenericType> enclosingClasses = type.getEnclosingClasses();

    typePathWriteStack.removeIf(writeProgress -> {
      StructTypePath path = writeProgress.getPaths().peek();
      if (path == null) {
        writeProgress.writeTypeAnnotation(sb);
        return true;
      }
      if (path.getTypePathKind() == StructTypePath.Kind.ARRAY.getOpcode() && type.arrayDim == writeProgress.getPaths().size()) {
        writeProgress.writeTypeAnnotation(sb);
        return true;
      }
      return false;
    });

    if (enclosingClasses.isEmpty()) {
      String name = type.value.replace('/', '.');
      sb.append(DecompilerContext.getImportCollector().getShortName(name));
    }
    else {
      for (GenericType tp : enclosingClasses) {
        if (sb.length() == 0) {
          sb.append(DecompilerContext.getImportCollector().getShortName(tp.value.replace('/', '.')));
        }
        else {
          sb.append(tp.value);
        }

        appendTypeArguments(tp, sb, typePathWriteStack);
        sb.append('.');
      }

      sb.append(type.value);
    }

    appendTypeArguments(type, sb, typePathWriteStack);
  }

  private static void appendTypeArguments(GenericType type, StringBuilder sb, List<TypePathWriteProgress> typePathWriteStack) {
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
        List<TypePathWriteProgress> locTypePathWriteStack = typePathWriteStack.stream().filter(writeProgress -> {
          StructTypePath path = writeProgress.getPaths().peek();
          boolean inGeneric = path != null && path.getTypeArgumentIndex() == it && path.getTypePathKind() == StructTypePath.Kind.TYPE.getOpcode();
          if (inGeneric) {
            writeProgress.getPaths().pop();
          }
          return inGeneric;
        }).collect(Collectors.toList());

        locTypePathWriteStack.removeIf(writeProgress -> {
          StructTypePath path = writeProgress.getPaths().peek();
          if (path == null && wildcard != GenericType.WILDCARD_NO) {
            writeProgress.writeTypeAnnotation(sb);
            return true;
          }
          if (path != null && path.getTypePathKind() == StructTypePath.Kind.TYPE.getOpcode() && path.getTypeArgumentIndex() == it &&
            genPar.arrayDim != 0 && genPar.arrayDim == writeProgress.getPaths().size()
          ) {
            writeProgress.writeTypeAnnotation(sb);
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


        typePathWriteStack.forEach(writeProgress -> { // remove all wild card entries
          StructTypePath path = writeProgress.getPaths().peek();
          boolean isWildCard = path != null && path.getTypePathKind() == StructTypePath.Kind.TYPE_WILDCARD.getOpcode();
          if (isWildCard && path.getTypeArgumentIndex() == it) writeProgress.getPaths().pop();
        });
        locTypePathWriteStack.removeIf(writeProgress -> {
          StructTypePath path = writeProgress.getPaths().peek();
          if (path != null && path.getTypeArgumentIndex() == it &&
            path.getTypePathKind() == StructTypePath.Kind.TYPE_WILDCARD.getOpcode() &&
            writeProgress.getPaths().size() - 1 == genPar.arrayDim
          ) {
            writeProgress.writeTypeAnnotation(sb);
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
