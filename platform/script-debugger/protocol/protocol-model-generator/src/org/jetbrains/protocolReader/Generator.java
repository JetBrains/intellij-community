// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.ItemDescriptor;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.*;

import static org.jetbrains.jsonProtocol.ProtocolMetaModel.*;

/**
 * Read metamodel and generates set of files with Java classes/interfaces for the protocol.
 */
class Generator {
  private static final String PARSER_INTERFACE_LIST_CLASS_NAME = "GeneratedReaderInterfaceList";
  static final String READER_INTERFACE_NAME = "ProtocolResponseReader";

  final List<String> jsonProtocolParserClassNames = new ArrayList<>();
  final List<ParserRootInterfaceItem> parserRootInterfaceItems = new ArrayList<>();
  final TypeMap typeMap = new TypeMap();

  private final FileSet fileSet;
  private final Naming naming;

  Generator(String outputDir, String rootPackage, String requestClassName) throws IOException {
    fileSet = new FileSet(FileSystems.getDefault().getPath(outputDir));
    naming = new Naming(rootPackage, requestClassName);
  }

  public Naming getNaming() {
    return naming;
  }

  public static final class Naming {
    public final ClassNameScheme params;
    public final ClassNameScheme additionalParam;
    public final ClassNameScheme outputTypedef;

    public final ClassNameScheme.Input commandResult;
    public final ClassNameScheme.Input eventData;
    public final ClassNameScheme inputValue;
    public final ClassNameScheme inputEnum;
    public final ClassNameScheme inputTypedef;

    public final ClassNameScheme commonTypedef;

    public final String inputPackage;
    public final String requestClassName;

    private Naming(String rootPackage, String requestClassName) {
      this.requestClassName = requestClassName;

      params = new ClassNameScheme.Output("", rootPackage);
      additionalParam = new ClassNameScheme.Output("", rootPackage);
      outputTypedef = new ClassNameScheme.Output("Typedef", rootPackage);
      commonTypedef = new ClassNameScheme.Common("Typedef", rootPackage);

      inputPackage = rootPackage;
      commandResult = new ClassNameScheme.Input("Result", inputPackage);
      eventData = new ClassNameScheme.Input("EventData", inputPackage);
      inputValue = new ClassNameScheme.Input("Value", inputPackage);
      inputEnum = new ClassNameScheme.Input("", inputPackage);
      inputTypedef = new ClassNameScheme.Input("Typedef", inputPackage);
    }
  }

  private static boolean isDomainSkipped(Domain domain) {
    if (domain.domain().equals("CSS") || domain.domain().equals("Inspector")) {
      return false;
    }

    // todo DOMDebugger
    return domain.hidden() ||
           domain.domain().equals("DOMDebugger") ||
           domain.domain().equals("Timeline") ||
           domain.domain().equals("Input");
  }

  void go(Root metamodel) throws IOException {
    initializeKnownTypes();

    List<Domain> domainList = metamodel.domains();
    Map<String, DomainGenerator> domainGeneratorMap = new HashMap<>();
    for (Domain domain : domainList) {
      if (isDomainSkipped(domain)) {
        System.out.println("Domain skipped: " + domain.domain());
        continue;
      }
      DomainGenerator domainGenerator = new DomainGenerator(this, domain);
      domainGeneratorMap.put(domain.domain(), domainGenerator);
      domainGenerator.registerTypes();
    }

    for (Domain domain : domainList) {
      if (!isDomainSkipped(domain)) {
        System.out.println("Domain generated: " + domain.domain());
      }
    }

    typeMap.setDomainGeneratorMap(domainGeneratorMap);

    for (DomainGenerator domainGenerator : domainGeneratorMap.values()) {
      domainGenerator.generateCommandsAndEvents();
    }

    typeMap.generateRequestedTypes();
    generateParserInterfaceList();
    generateParserRoot(parserRootInterfaceItems);
    fileSet.deleteOtherFiles();
  }

  QualifiedTypeData resolveType(final ItemDescriptor typedObject, final ResolveAndGenerateScope scope) {
    UnqualifiedTypeData unqualifiedType = switchByType(typedObject, new TypeVisitor<UnqualifiedTypeData>() {
      @Override
      public UnqualifiedTypeData visitRef(String refName) {
        return new UnqualifiedTypeData(resolveRefType(scope.getDomainName(), refName, scope.getTypeDirection()));
      }

      @Override
      public UnqualifiedTypeData visitBoolean() {
        return UnqualifiedTypeData.BOOLEAN;
      }

      @Override
      public UnqualifiedTypeData visitEnum(List<String> enumConstants) {
        assert scope instanceof MemberScope;
        return new UnqualifiedTypeData(((MemberScope)scope).generateEnum(typedObject.description(), enumConstants));
      }

      @Override
      public UnqualifiedTypeData visitString() {
        return UnqualifiedTypeData.STRING;
      }

      @Override
      public UnqualifiedTypeData visitInteger() {
        return UnqualifiedTypeData.INT;
      }

      @Override
      public UnqualifiedTypeData visitNumber() {
        return UnqualifiedTypeData.NUMBER;
      }

      @Override
      public UnqualifiedTypeData visitMap() {
        return UnqualifiedTypeData.MAP;
      }

      @Override
      public UnqualifiedTypeData visitArray(ArrayItemType items) {
        return new UnqualifiedTypeData(new ListType(scope.resolveType(items).getJavaType()));
      }

      @Override
      public UnqualifiedTypeData visitObject(List<ObjectProperty> properties) {
        return new UnqualifiedTypeData(scope.generateNestedObject(typedObject.description(), properties), false);
      }

      @Override
      public UnqualifiedTypeData visitUnknown() {
        return UnqualifiedTypeData.ANY;
      }
    });

    return unqualifiedType.getQualifiedType(typedObject instanceof ItemDescriptor.Named && ((ItemDescriptor.Named)typedObject).optional());
  }

  private static class UnqualifiedTypeData {
    private final BoxableType typeRef;
    private final boolean nullable;
    private final boolean asRawString;

    UnqualifiedTypeData(BoxableType typeRef) {
      this(typeRef, false);
    }

    UnqualifiedTypeData(BoxableType typeRef, boolean nullable) {
      this(typeRef, nullable, false);
    }

    UnqualifiedTypeData(BoxableType typeRef, boolean nullable, boolean asRawString) {
      this.typeRef = typeRef;
      this.nullable = nullable;
      this.asRawString = asRawString;
    }

    QualifiedTypeData getQualifiedType(boolean optional) {
      return new QualifiedTypeData(typeRef, optional, nullable, asRawString);
    }

    static final UnqualifiedTypeData BOOLEAN = new UnqualifiedTypeData(BoxableType.BOOLEAN, false);
    static final UnqualifiedTypeData STRING = new UnqualifiedTypeData(BoxableType.STRING, false);
    static final UnqualifiedTypeData INT = new UnqualifiedTypeData(BoxableType.INT, false);
    static final UnqualifiedTypeData MAP = new UnqualifiedTypeData(BoxableType.MAP, false);
    static final UnqualifiedTypeData NUMBER = new UnqualifiedTypeData(BoxableType.NUMBER, false);
    static final UnqualifiedTypeData ANY = new UnqualifiedTypeData(BoxableType.STRING, false, true);
  }

  private void generateParserInterfaceList() throws IOException {
    FileUpdater fileUpdater = startJavaFile(getNaming().inputPackage, PARSER_INTERFACE_LIST_CLASS_NAME + ".java");
    // Write classes in stable order.
    Collections.sort(jsonProtocolParserClassNames);

    TextOutput out = fileUpdater.out;
    out.append("public class ").append(PARSER_INTERFACE_LIST_CLASS_NAME).openBlock();
    out.append("public static final Class<?>[] LIST =").openBlock();
    for (String name : jsonProtocolParserClassNames) {
      out.append(name).append(".class,").newLine();
    }
    out.closeBlock();
    out.semi();
    out.closeBlock();
    fileUpdater.update();
  }

  private void generateParserRoot(List<ParserRootInterfaceItem> parserRootInterfaceItems) throws IOException {
    FileUpdater fileUpdater = startJavaFile(getNaming().inputPackage, READER_INTERFACE_NAME + ".java");
    // Write classes in stable order.
    Collections.sort(parserRootInterfaceItems);

    TextOutput out = fileUpdater.out;
    out.append("public abstract class ").append(READER_INTERFACE_NAME).space().append("implements org.jetbrains.jsonProtocol.ResponseResultReader").openBlock();
    for (ParserRootInterfaceItem item : parserRootInterfaceItems) {
      item.writeCode(out);
    }
    out.newLine().newLine().append("@Override").newLine().append("public Object readResult(String methodName, org.jetbrains.io.JsonReaderEx reader)");
    out.openBlock();

    boolean isNotFirst = false;
    for (ParserRootInterfaceItem item : parserRootInterfaceItems) {
      if (isNotFirst) {
        out.append("else ");
      }
      else {
        isNotFirst = true;
      }
      out.append("if (methodName.equals(\"");
      if (!item.domain.isEmpty()) {
        out.append(item.domain).append('.');
      }
      out.append(item.name).append('"').append(")) return ");
      item.appendReadMethodName(out);
      out.append("(reader)").semi().newLine();
    }
    out.append("else throw new IllegalArgumentException(methodName)").semi();
    out.closeBlock();

    out.closeBlock();
    fileUpdater.update();
  }

  /**
   * Resolve absolute (DOMAIN.TYPE) or relative (TYPE) type name
   */
  private BoxableType resolveRefType(String scopeDomainName, String refName,
                                     TypeData.Direction direction) {
    int pos = refName.indexOf('.');
    String domainName;
    String shortName;
    if (pos == -1) {
      domainName = scopeDomainName;
      shortName = refName;
    }
    else {
      domainName = refName.substring(0, pos);
      shortName = refName.substring(pos + 1);
    }
    return typeMap.resolve(domainName, shortName, direction);
  }

  static String generateMethodNameSubstitute(String originalName, TextOutput out) {
    if (!BAD_METHOD_NAMES.contains(originalName)) {
      return originalName;
    }
    out.append("@org.chromium.protocolReader.JsonField(jsonLiteralName=\"").append(originalName).append("\")").newLine();
    return "get" + Character.toUpperCase(originalName.charAt(0)) + originalName.substring(1);
  }

  static String capitalizeFirstChar(String s) {
    if (!s.isEmpty() && Character.isLowerCase(s.charAt(0))) {
      s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    return s;
  }

  FileUpdater startJavaFile(ClassNameScheme nameScheme, Domain domain, String baseName) throws IOException {
    return startJavaFile(nameScheme.getPackageNameVirtual(domain.domain()), nameScheme.getShortName(baseName) + ".java");
  }

  private FileUpdater startJavaFile(String packageName, String filename) {
    FileUpdater fileUpdater = fileSet.createFileUpdater(packageName.replace('.', '/') + '/' + filename);
    fileUpdater.out.append("// Generated source").newLine().append("package ").append(packageName).semi().newLine().newLine();
    return fileUpdater;
  }

  static <R> R switchByType(@NotNull ItemDescriptor typedObject, @NotNull TypeVisitor<R> visitor) {
    String refName = typedObject instanceof ItemDescriptor.Referenceable ? ((ItemDescriptor.Referenceable)typedObject).ref() : null;
    if (refName != null) {
      return visitor.visitRef(refName);
    }
    String typeName = typedObject.type();
    switch (typeName) {
      case BOOLEAN_TYPE:
        return visitor.visitBoolean();
      case STRING_TYPE:
        if (typedObject.getEnum() != null) {
          return visitor.visitEnum(typedObject.getEnum());
        }
        return visitor.visitString();
      case INTEGER_TYPE:
      case "int":
        return visitor.visitInteger();
      case NUMBER_TYPE:
        return visitor.visitNumber();
      case ARRAY_TYPE:
        return visitor.visitArray(typedObject.items());
      case OBJECT_TYPE:
        if (!(typedObject instanceof ItemDescriptor.Type)) {
          return visitor.visitObject(null);
        }

        List<ObjectProperty> properties = ((ItemDescriptor.Type)typedObject).properties();
        if (properties == null || properties.isEmpty()) {
          return visitor.visitMap();
        }
        else {
          return visitor.visitObject(properties);
        }
      case ANY_TYPE:
        return visitor.visitUnknown();
      case UNKNOWN_TYPE:
        return visitor.visitUnknown();
    }
    throw new RuntimeException("Unrecognized type " + typeName);
  }

  private static void initializeKnownTypes() {
    // Code example:
    // typeMap.getTypeData("Page", "Cookie").getInput().setJavaTypeName("Object");
  }

  private static final Set<String> BAD_METHOD_NAMES = new HashSet<>(Collections.singletonList("this"));
}