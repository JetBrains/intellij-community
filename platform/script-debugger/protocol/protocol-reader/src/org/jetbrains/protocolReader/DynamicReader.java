// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

import java.util.*;

public class DynamicReader<ROOT> {
  final LinkedHashMap<Class<?>, TypeHandler<?>> typeToTypeHandler;
  private final ReaderRoot<ROOT> root;

  public DynamicReader(Class<ROOT> readerRootClass, Class<?>[] protocolInterfaces) {
    typeToTypeHandler = new InterfaceReader(protocolInterfaces).go();
    root = new ReaderRoot<>(readerRootClass, typeToTypeHandler);
  }

  public GeneratedCodeMap generateStaticReader(StringBuilder stringBuilder, String packageName, String className,
                                               Collection<GeneratedCodeMap> basePackages) {
    final GlobalScope globalScope = new GlobalScope(typeToTypeHandler.values(), basePackages);
    FileScope fileScope = globalScope.newFileScope(stringBuilder);
    final TextOutput out = fileScope.getOutput();
    out.append("// Generated source");
    out.newLine().append("package ").append(packageName).append(';');
    out.newLine().newLine().append("import org.jetbrains.jsonProtocol.*;");
    out.newLine().newLine().append("import static org.jetbrains.jsonProtocol.JsonReaders.*;");
    out.newLine().newLine().append("public final class ").append(className).space();
    out.append(root.getType().isInterface() ? "implements" : "extends").space().append(root.getType().getCanonicalName()).openBlock(
      false);

    final ClassScope rootClassScope = fileScope.newClassScope();
    root.writeStaticMethodJava(rootClassScope);

    for (TypeHandler<?> typeHandler : typeToTypeHandler.values()) {
      out.newLine();
      typeHandler.writeStaticClassJava(rootClassScope);
      out.newLine();
    }

    for (TypeHandler<?> typeHandler : globalScope.getTypeFactories()) {
      String name = globalScope.getTypeImplShortName(typeHandler);
      String originName = typeHandler.getTypeClass().getCanonicalName();
      out.newLine().append("static final class ").append(name).append(Util.TYPE_FACTORY_NAME_POSTFIX).append(" extends ObjectFactory<");
      out.append(originName).append('>').openBlock();
      out.append("@Override").newLine().append("public ").append(originName).append(" read(").append(Util.JSON_READER_PARAMETER_DEF);
      out.append(')').openBlock();
      out.append("return ");
      typeHandler.writeInstantiateCode(rootClassScope, out);
      out.append('(').append(Util.READER_NAME).append(");").closeBlock();
      out.closeBlock();
      out.newLine();
    }

    out.closeBlock();

    Map<Class<?>, String> typeToImplClassName = new HashMap<>();
    for (TypeHandler<?> typeHandler : typeToTypeHandler.values()) {
      String shortName = fileScope.getTypeImplShortName(typeHandler);
      String fullReference = packageName + "." + className + "." + shortName;
      typeToImplClassName.put(typeHandler.getTypeClass(), fullReference);
    }

    return new GeneratedCodeMap(typeToImplClassName);
  }
}