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
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.util.ArrayList;
import java.util.List;

public class GenericMain {

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
      GenericFieldDescriptor descriptor = new GenericFieldDescriptor();
      descriptor.type = new GenericType(signature);
      return descriptor;
    }
    catch (RuntimeException e) {
      DecompilerContext.getLogger().writeMessage("Invalid signature: " + signature, IFernflowerLogger.Severity.WARN);
      return null;
    }
  }

  public static GenericMethodDescriptor parseMethodSignature(String signature) {
    String original = signature;
    try {
      GenericMethodDescriptor descriptor = new GenericMethodDescriptor();

      signature = parseFormalParameters(signature, descriptor.fparameters, descriptor.fbounds);

      int to = signature.indexOf(")");
      String pars = signature.substring(1, to);
      signature = signature.substring(to + 1);

      while (pars.length() > 0) {
        String par = GenericType.getNextType(pars);
        descriptor.params.add(new GenericType(par));
        pars = pars.substring(par.length());
      }

      String par = GenericType.getNextType(signature);
      descriptor.ret = new GenericType(par);
      signature = signature.substring(par.length());

      if (signature.length() > 0) {
        String[] exceptions = signature.split("\\^");

        for (int i = 1; i < exceptions.length; i++) {
          descriptor.exceptions.add(new GenericType(exceptions[i]));
        }
      }

      return descriptor;
    }
    catch (RuntimeException e) {
      DecompilerContext.getLogger().writeMessage("Invalid signature: " + original, IFernflowerLogger.Severity.WARN);
      return null;
    }
  }

  private static String parseFormalParameters(String signature, List<String> parameters, List<List<GenericType>> bounds) {
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

  public static String getGenericCastTypeName(GenericType type) {
    String s = getTypeName(type);
    int dim = type.arrayDim;
    while (dim-- > 0) {
      s += "[]";
    }
    return s;
  }

  private static String getTypeName(GenericType type) {
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
      appendClassName(type, buffer);
      return buffer.toString();
    }

    throw new RuntimeException("Invalid type: " + type);
  }

  private static void appendClassName(GenericType type, StringBuilder buffer) {
    List<GenericType> enclosingClasses = type.getEnclosingClasses();

    if (enclosingClasses.isEmpty()) {
      String name = type.value.replace('/', '.');
      buffer.append(DecompilerContext.getImportCollector().getShortName(name));
    }
    else {
      for (GenericType tp : enclosingClasses) {
        if (buffer.length() == 0) {
          buffer.append(DecompilerContext.getImportCollector().getShortName(tp.value.replace('/', '.')));
        }
        else {
          buffer.append(tp.value);
        }

        appendTypeArguments(tp, buffer);
        buffer.append('.');
      }

      buffer.append(type.value);
    }

    appendTypeArguments(type, buffer);
  }

  private static void appendTypeArguments(GenericType type, StringBuilder buffer) {
    if (!type.getArguments().isEmpty()) {
      buffer.append('<');

      for (int i = 0; i < type.getArguments().size(); i++) {
        if (i > 0) {
          buffer.append(", ");
        }

        int wildcard = type.getWildcards().get(i);
        switch (wildcard) {
          case GenericType.WILDCARD_UNBOUND:
            buffer.append('?');
            break;
          case GenericType.WILDCARD_EXTENDS:
            buffer.append("? extends ");
            break;
          case GenericType.WILDCARD_SUPER:
            buffer.append("? super ");
            break;
        }

        GenericType genPar = type.getArguments().get(i);
        if (genPar != null) {
          buffer.append(getGenericCastTypeName(genPar));
        }
      }

      buffer.append(">");
    }
  }
}
