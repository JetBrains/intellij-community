package org.jetbrains.protocolReader;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DynamicReader<ROOT> {
  final LinkedHashMap<Class<?>, TypeHandler<?>> typeToTypeHandler;
  private final ReaderRoot<ROOT> root;

  public DynamicReader(Class<ROOT> readerRootClass, Class<?>[] protocolInterfaces) {
    typeToTypeHandler = new InterfaceReader(protocolInterfaces).go();
    root = new ReaderRoot<>(readerRootClass, typeToTypeHandler);
  }

  @NotNull
  public GeneratedCodeMap generateReader(StringBuilder stringBuilder, String packageName, String className,
                                         Collection<GeneratedCodeMap> basePackages) {
    GlobalScope globalScope = new GlobalScope(typeToTypeHandler.values(), basePackages);
    FileScope fileScope = globalScope.newFileScope(stringBuilder);
    TextOutput out = fileScope.getOutput();
    out.append("// Generated source");
    out.newLine().append("package ").append(packageName).append(';');
    out.newLine().newLine().append("import org.jetbrains.jsonProtocol.*;");
    out.newLine().newLine().append("import static org.jetbrains.jsonProtocol.JsonReaders.*;");
    out.newLine().newLine().append("public final class ").append(className).space();
    out.append(root.getType().isInterface() ? "implements" : "extends").space().append(root.getType().getCanonicalName()).openBlock(false);

    ClassScope rootClassScope = fileScope.newClassScope();
    root.writeStaticMethodJava(rootClassScope);

    for (TypeHandler<?> typeHandler : typeToTypeHandler.values()) {
      out.newLine();
      typeHandler.writeStaticClassJava(rootClassScope);
      out.newLine();
    }

    boolean isFirst = true;
    for (TypeHandler<?> typeHandler : globalScope.getTypeFactories()) {
      if (isFirst) {
        isFirst = false;
      }
      else {
        out.newLine();
      }

      String originName = typeHandler.getTypeClass().getCanonicalName();
      out.newLine().append("private static final class ").append(globalScope.getTypeImplShortName(typeHandler)).append(Util.TYPE_FACTORY_NAME_POSTFIX).append(" extends ObjectFactory<");
      out.append(originName).append('>').openBlock();
      out.append("@Override").newLine().append("public ").append(originName).append(" read(").append(Util.JSON_READER_PARAMETER_DEF);
      out.append(')').openBlock();
      out.append("return ");
      typeHandler.writeInstantiateCode(rootClassScope, out);
      out.append('(').append(Util.READER_NAME).append(", null);").closeBlock();
      out.closeBlock();
    }

    out.closeBlock();

    Map<Class<?>, String> typeToImplClassName = new THashMap<>();
    for (TypeHandler<?> typeHandler : typeToTypeHandler.values()) {
      typeToImplClassName.put(typeHandler.getTypeClass(), packageName + "." + className + "." + fileScope.getTypeImplShortName(typeHandler));
    }

    return new GeneratedCodeMap(typeToImplClassName);
  }
}