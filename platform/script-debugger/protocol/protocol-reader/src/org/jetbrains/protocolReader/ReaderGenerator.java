package org.jetbrains.protocolReader;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.*;

public final class ReaderGenerator {
  public static void generate(String[] args, @NotNull GenerateConfiguration configuration) throws IOException {
    FileUpdater fileUpdater = new FileUpdater(FileSystems.getDefault().getPath(parseArgs(args).outputDirectory(),
                                                                               configuration.packageName.replace('.', File.separatorChar),
                                                                               configuration.className + ".java"));
    generate(configuration, fileUpdater.builder);
    fileUpdater.update();
  }

  public static class GenerateConfiguration<ROOT> {
    private final String packageName;
    private final String className;
    final Collection<Map<Class<?>, String>> basePackagesMap;

    final LinkedHashMap<Class<?>, TypeWriter<?>> typeToTypeHandler;
    final ReaderRoot<ROOT> root;

    public GenerateConfiguration(String packageName, String className, Class<ROOT> readerRootClass, Class<?>[] protocolInterfaces) {
      this(packageName, className, readerRootClass, protocolInterfaces, null);
    }

    public GenerateConfiguration(String packageName, String className, Class<ROOT> readerRootClass, Class<?>[] protocolInterfaces, @Nullable Map<Class<?>, String> basePackagesMap) {
      this.packageName = packageName;
      this.className = className;
      this.basePackagesMap = basePackagesMap == null ? Collections.emptyList() : Collections.singletonList(basePackagesMap);

      typeToTypeHandler = new InterfaceReader(protocolInterfaces).go();
      root = new ReaderRoot<>(readerRootClass, typeToTypeHandler);
    }
  }

  private interface Params {
    String outputDirectory();
  }

  private static Params parseArgs(String[] args) {
    final StringParam outputDirParam = new StringParam();

    Map<String, StringParam> paramMap = new HashMap<>(3);
    paramMap.put("output-dir", outputDirParam);

    for (String arg : args) {
      if (!arg.startsWith("--")) {
        throw new IllegalArgumentException("Unrecognized param: " + arg);
      }
      int equalsPos = arg.indexOf('=', 2);
      String key;
      String value;
      if (equalsPos == -1) {
        key = arg.substring(2).trim();
        value = null;
      }
      else {
        key = arg.substring(2, equalsPos).trim();
        value = arg.substring(equalsPos + 1).trim();
      }
      StringParam paramListener = paramMap.get(key);
      if (paramListener == null) {
        throw new IllegalArgumentException("Unrecognized param name: " + key);
      }
      try {
        paramListener.setValue(value);
      }
      catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Failed to set value of " + key, e);
      }
    }
    for (Map.Entry<String, StringParam> en : paramMap.entrySet()) {
      if (en.getValue().getValue() == null) {
        throw new IllegalArgumentException("Parameter " + en.getKey() + " should be set");
      }
    }

    return outputDirParam::getValue;
  }

  private static class StringParam {
    private String value;

    public void setValue(String value) {
      if (value == null) {
        throw new IllegalArgumentException("Argument with value expected");
      }
      if (this.value != null) {
        throw new IllegalArgumentException("Argument value already set");
      }
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public static Map<Class<?>, String> buildParserMap(GenerateConfiguration<?> configuration) {
    FileScope fileScope = generate(configuration, new StringBuilder());

    Map<Class<?>, String> typeToImplClassName = new THashMap<>();
    for (TypeWriter<?> typeWriter : configuration.typeToTypeHandler.values()) {
      typeToImplClassName.put(typeWriter.typeClass, configuration.packageName + "." + configuration.className + "." + fileScope.getTypeImplShortName(typeWriter));
    }
    return typeToImplClassName;
  }

  @NotNull
  private static FileScope generate(GenerateConfiguration<?> configuration, StringBuilder stringBuilder) {
    GlobalScope globalScope = new GlobalScope(configuration.typeToTypeHandler.values(), configuration.basePackagesMap);
    FileScope fileScope = globalScope.newFileScope(stringBuilder);
    TextOutput out = fileScope.getOutput();
    out.append("// Generated source");
    out.newLine().append("package ").append(configuration.packageName).append(';');
    out.newLine().newLine().append("import org.jetbrains.jsonProtocol.*;");
    out.newLine().newLine().append("import org.jetbrains.annotations.NotNull;");
    out.newLine().newLine().append("import static org.jetbrains.jsonProtocol.JsonReaders.*;");
    out.newLine().newLine().append("public final class ").append(configuration.className).space();
    out.append(configuration.root.getType().isInterface() ? "implements" : "extends").space().append(configuration.root.getType().getCanonicalName()).openBlock(false);

    ClassScope rootClassScope = fileScope.newClassScope();
    configuration.root.writeStaticMethodJava(rootClassScope);

    for (TypeWriter<?> typeWriter : configuration.typeToTypeHandler.values()) {
      out.newLine();
      typeWriter.write(rootClassScope);
      out.newLine();
    }

    boolean isFirst = true;
    for (TypeWriter<?> typeWriter : globalScope.getTypeFactories()) {
      if (isFirst) {
        isFirst = false;
      }
      else {
        out.newLine();
      }

      String originName = typeWriter.typeClass.getCanonicalName();
      out.newLine().append("private static final class ").append(globalScope.getTypeImplShortName(typeWriter)).append(Util.TYPE_FACTORY_NAME_POSTFIX).append(" extends ObjectFactory<");
      out.append(originName).append('>').openBlock();
      out.append("@Override").newLine().append("public ").append(originName).append(" read(").append(Util.JSON_READER_PARAMETER_DEF);
      out.append(')').openBlock();
      out.append("return ");
      typeWriter.writeInstantiateCode(rootClassScope, out);
      out.append('(').append(Util.READER_NAME).append(", null);").closeBlock();
      out.closeBlock();
    }

    out.closeBlock();
    return fileScope;
  }
}
