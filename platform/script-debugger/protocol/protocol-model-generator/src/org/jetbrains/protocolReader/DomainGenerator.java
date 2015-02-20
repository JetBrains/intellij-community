package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jsonProtocol.ItemDescriptor;
import org.jetbrains.jsonProtocol.ProtocolMetaModel;

import java.io.IOException;
import java.util.List;

class DomainGenerator {
  final ProtocolMetaModel.Domain domain;
  final Generator generator;

  DomainGenerator(Generator generator, ProtocolMetaModel.Domain domain) {
    this.generator = generator;
    this.domain = domain;
  }

  void registerTypes() {
    if (domain.types() != null) {
      for (ProtocolMetaModel.StandaloneType type : domain.types()) {
        generator.typeMap.getTypeData(domain.domain(), type.id()).setType(type);
      }
    }
  }

  @NotNull
  static String fixMethodName(@NotNull String name) {
    int i = name.indexOf("breakpoint");
    return i > 0 ? name.substring(0, i) + 'B' + name.substring(i + 1) : name;
  }

  void generateCommandsAndEvents() throws IOException {
    FileUpdater requestsFileUpdater = generator.startJavaFile(generator.naming.params.getPackageNameVirtual(domain.domain()), "Requests.java");
    TextOutput out = requestsFileUpdater.out;
    out.append("import org.jetbrains.annotations.NotNull;").newLine();
    out.append("import org.jetbrains.jsonProtocol.Request;").newLine();
    out.newLine().append("public final class ").append("Requests");
    out.openBlock();

    boolean isFirst = true;

    for (ProtocolMetaModel.Command command : domain.commands()) {
      boolean hasResponse = command.returns() != null;

      boolean onlyMandatoryParams = true;
      List<ProtocolMetaModel.Parameter> params = command.parameters();
      boolean hasParams = params != null && !params.isEmpty();
      if (hasParams) {
        for (ProtocolMetaModel.Parameter parameter : params) {
          if (parameter.optional()) {
            onlyMandatoryParams = false;
          }
        }
      }

      String returnType = hasResponse ? generator.naming.commandResult.getShortName(command.name()) : "Void";
      if (onlyMandatoryParams) {
        if (isFirst) {
          isFirst = false;
        }
        else {
          out.newLine().newLine();
        }
        out.append("@NotNull").newLine().append("public static Request<");
        out.append(returnType);
        out.append(">").space().append(fixMethodName(command.name())).append("(");

        OutputClassScope classScope = new OutputClassScope(this, generator.naming.params.getFullName(domain.domain(), command.name()));
        BoxableType[] parameterTypes = hasParams ? new BoxableType[params.size()] : null;
        if (hasParams) {
          classScope.writeMethodParameters(out, params, parameterTypes);
        }

        out.append(')').openBlock();

        if (hasParams) {
          out.append("V8SimpleRequest<").append(returnType).append(">").append(" r =");
        }
        else {
          out.append("return");
        }

        out.append(" new V8SimpleRequest<").append(returnType).append(">(\"");
        if (!domain.domain().isEmpty()) {
          out.append(domain.domain()).append('.');
        }
        out.append(command.name()).append("\")").semi();

        if (hasParams) {
          classScope.writeWriteCalls(out, params, parameterTypes, "r");
          out.newLine().append("return r").semi();
        }

        out.closeBlock();
      }
      else {
        generateRequest(command, returnType);
      }

      if (hasResponse) {
        FileUpdater fileUpdater = generator.startJavaFile(generator.naming.commandResult, domain, command.name());
        generateJsonProtocolInterface(fileUpdater.out, generator.naming.commandResult.getShortName(command.name()), command.description(), command.returns(), null);
        fileUpdater.update();
        generator.jsonProtocolParserClassNames.add(generator.naming.commandResult.getFullName(domain.domain(), command.name()).getFullText());
        generator.parserRootInterfaceItems.add(new ParserRootInterfaceItem(domain.domain(), command.name(), generator.naming.commandResult));
      }
    }

    out.closeBlock();
    requestsFileUpdater.update();

    if (domain.events() != null) {
      for (ProtocolMetaModel.Event event : domain.events()) {
        generateEvenData(event);
        generator.jsonProtocolParserClassNames.add(generator.naming.eventData.getFullName(domain.domain(), event.name()).getFullText());
        generator.parserRootInterfaceItems.add(new ParserRootInterfaceItem(domain.domain(), event.name(), generator.naming.eventData));
      }
    }
  }

  private void generateRequest(@NotNull final ProtocolMetaModel.Command command, @NotNull final String returnType) throws IOException {
    TextOutConsumer baseTypeBuilder = new TextOutConsumer() {
      @Override
      public void append(TextOutput out) {
        out.space().append("extends ").append(generator.naming.requestClassName).append('<').append(returnType).append('>');
      }
    };

    TextOutConsumer memberBuilder = new TextOutConsumer() {
      @Override
      public void append(TextOutput out) {
        out.append("@NotNull").newLine().append("@Override").newLine().append("public String getMethodName()").openBlock();
        out.append("return \"");
        if (!domain.domain().isEmpty()) {
          out.append(domain.domain()).append('.');
        }
        out.append(command.name()).append("\";").closeBlock();
      }
    };
    generateTopLevelOutputClass(generator.naming.params, command.name(), command.description(), baseTypeBuilder,
                                memberBuilder, command.parameters());
  }

  void generateCommandAdditionalParam(ProtocolMetaModel.StandaloneType type) throws IOException {
    generateTopLevelOutputClass(generator.naming.additionalParam, type.id(), type.description(), null, null, type.properties());
  }

  private <P extends ItemDescriptor.Named> void generateTopLevelOutputClass(@NotNull ClassNameScheme nameScheme,
                                                                            String baseName,
                                                                            String description,
                                                                            @Nullable TextOutConsumer baseType,
                                                                            @Nullable TextOutConsumer additionalMemberText,
                                                                            List<P> properties) throws IOException {
    FileUpdater fileUpdater = generator.startJavaFile(nameScheme, domain, baseName);
    if (nameScheme == generator.naming.params) {
      fileUpdater.out.append("import org.jetbrains.annotations.NotNull;").newLine().newLine();
    }
    generateOutputClass(fileUpdater.out, nameScheme.getFullName(domain.domain(), baseName), description, baseType, additionalMemberText, properties);
    fileUpdater.update();
  }

  private <P extends ItemDescriptor.Named> void generateOutputClass(@NotNull TextOutput out,
                                                                    NamePath classNamePath,
                                                                    String description,
                                                                    @Nullable TextOutConsumer baseType,
                                                                    TextOutConsumer additionalMemberText,
                                                                    List<P> properties) {
    out.doc(description);
    out.append("public final class ").append(classNamePath.getLastComponent());
    if (baseType == null) {
      out.append(" extends ").append("org.jetbrains.jsonProtocol.OutMessage");
    }
    else {
      baseType.append(out);
    }

    OutputClassScope classScope = new OutputClassScope(this, classNamePath);
    if (additionalMemberText != null) {
      classScope.addMember(additionalMemberText);
    }

    out.openBlock();
    classScope.generate(out, properties);
    classScope.writeAdditionalMembers(out);
    out.closeBlock();
  }

  StandaloneTypeBinding createStandaloneOutputTypeBinding(@NotNull ProtocolMetaModel.StandaloneType type, @NotNull String name) {
    return Generator.switchByType(type, new MyCreateStandaloneTypeBindingVisitorBase(this, type, name));
  }

  StandaloneTypeBinding createStandaloneInputTypeBinding(ProtocolMetaModel.StandaloneType type) {
    return Generator.switchByType(type, new CreateStandaloneTypeBindingVisitorBase(this, type) {
      @Override
      public StandaloneTypeBinding visitObject(List<ProtocolMetaModel.ObjectProperty> properties) {
        return createStandaloneObjectInputTypeBinding(getType(), properties);
      }

      @Override
      public StandaloneTypeBinding visitEnum(List<String> enumConstants) {
        return createStandaloneEnumInputTypeBinding(getType(), enumConstants,
                                                    TypeData.Direction.INPUT);
      }

      @Override
      public StandaloneTypeBinding visitArray(ProtocolMetaModel.ArrayItemType items) {
        ResolveAndGenerateScope resolveAndGenerateScope = new ResolveAndGenerateScope() {
          // This class is responsible for generating ad hoc type.
          // If we ever are to do it, we should generate into string buffer and put strings
          // inside TypeDef class.
          @Override
          public String getDomainName() {
            return domain.domain();
          }

          @Override
          public TypeData.Direction getTypeDirection() {
            return TypeData.Direction.INPUT;
          }

          @Override
          public <T extends ItemDescriptor> TypeDescriptor resolveType(T typedObject) {
            throw new UnsupportedOperationException();
          }

          @Override
          public BoxableType generateNestedObject(String description, List<ProtocolMetaModel.ObjectProperty> properties) {
            throw new UnsupportedOperationException();
          }
        };
        BoxableType itemBoxableType = generator.resolveType(items, resolveAndGenerateScope).getType();

        final BoxableType arrayType = new ListType(itemBoxableType);
        StandaloneTypeBinding.Target target = new StandaloneTypeBinding.Target() {
          @Override
          public BoxableType resolve(ResolveContext context) {
            return arrayType;
          }
        };

        return createTypedefTypeBinding(getType(), target, generator.naming.inputTypedef, TypeData.Direction.INPUT);
      }
    });
  }

  StandaloneTypeBinding createStandaloneObjectInputTypeBinding(@NotNull final ProtocolMetaModel.StandaloneType type, @Nullable final List<ProtocolMetaModel.ObjectProperty> properties) {
    final String name = type.id();
    final NamePath fullTypeName = generator.naming.inputValue.getFullName(domain.domain(), name);
    generator.jsonProtocolParserClassNames.add(fullTypeName.getFullText());

    return new StandaloneTypeBinding() {
      @Override
      public BoxableType getJavaType() {
        return new StandaloneType(fullTypeName, "writeMessage");
      }

      @Override
      public void generate() throws IOException {
        NamePath className = generator.naming.inputValue.getFullName(domain.domain(), name);
        FileUpdater fileUpdater = generator.startJavaFile(generator.naming.inputValue, domain, name);
        TextOutput out = fileUpdater.out;
        if (type.description() != null) {
          out.doc(type.description());
        }

        out.append("@org.jetbrains.jsonProtocol.JsonType").newLine();
        out.append("public interface ").append(className.getLastComponent()).openBlock();
        InputClassScope classScope = new InputClassScope(DomainGenerator.this, className);
        if (properties != null) {
          classScope.generateDeclarationBody(out, properties);
        }
        classScope.writeAdditionalMembers(out);
        out.closeBlock();
        fileUpdater.update();
      }

      @Override public TypeData.Direction getDirection() {
        return TypeData.Direction.INPUT;
      }
    };
  }

  StandaloneTypeBinding createStandaloneEnumInputTypeBinding(final ProtocolMetaModel.StandaloneType type,
                                                             final List<String> enumConstants, final TypeData.Direction direction) {
    final String name = type.id();
    return new StandaloneTypeBinding() {
      @Override
      public BoxableType getJavaType() {
        return new StandaloneType(generator.naming.inputEnum.getFullName(domain.domain(), name), "writeEnum");
      }

      @Override
      public void generate() throws IOException {
        FileUpdater fileUpdater = generator.startJavaFile(generator.naming.inputEnum, domain, name);
        fileUpdater.out.doc(type.description());
        Enums.appendEnums(enumConstants, generator.naming.inputEnum.getShortName(name), true, fileUpdater.out);
        fileUpdater.update();
      }

      @Override
      public TypeData.Direction getDirection() {
        return direction;
      }
    };
  }

  /**
   * Typedef is an empty class that just holds description and
   * refers to an actual type (such as String).
   */
  StandaloneTypeBinding createTypedefTypeBinding(final ProtocolMetaModel.StandaloneType type, StandaloneTypeBinding.Target target,
                                                 final ClassNameScheme nameScheme, final TypeData.Direction direction) {
    final String name = type.id();
    final NamePath typedefJavaName = nameScheme.getFullName(domain.domain(), name);
    final BoxableType actualJavaType = target.resolve(new StandaloneTypeBinding.Target.ResolveContext() {
      @Override
      public BoxableType generateNestedObject(String shortName, String description, List<ProtocolMetaModel.ObjectProperty> properties) {
        NamePath classNamePath = new NamePath(shortName, typedefJavaName);
        if (direction == null) {
          throw new RuntimeException("Unsupported");
        }

        switch (direction) {
          case INPUT:
            throw new RuntimeException("TODO");
          case OUTPUT:
            TextOutput out = new TextOutput(new StringBuilder());
            generateOutputClass(out, classNamePath, description, null, null, properties);
            break;
          default:
            throw new RuntimeException();
        }
        return new StandaloneType(new NamePath(shortName, typedefJavaName), "writeMessage");
      }
    });

    return new StandaloneTypeBinding() {
      @Override
      public BoxableType getJavaType() {
        return actualJavaType;
      }

      @Override
      public void generate() {
      }

      @Override
      public TypeData.Direction getDirection() {
        return direction;
      }
    };
  }

  private void generateEvenData(final ProtocolMetaModel.Event event) throws IOException {
    String className = generator.naming.eventData.getShortName(event.name());
    FileUpdater fileUpdater = generator.startJavaFile(generator.naming.eventData, domain, event.name());
    final String domainName = domain.domain();
    final CharSequence fullName = generator.naming.eventData.getFullName(domainName, event.name()).getFullText();
    generateJsonProtocolInterface(fileUpdater.out, className, event.description(), event.parameters(), new TextOutConsumer() {
      @Override
      public void append(TextOutput out) {
        out.newLine().append("org.jetbrains.wip.protocol.WipEventType<").append(fullName).append("> TYPE").newLine();
        out.append("\t= new org.jetbrains.wip.protocol.WipEventType<").append(fullName).append(">");
        out.append("(\"").append(domainName).append('.').append(event.name()).append("\", ").append(fullName).append(".class)").openBlock();
        {
          out.append("@Override").newLine().append("public ").append(fullName).append(" read(");
          out.append(generator.naming.inputPackage).append('.').append(Generator.READER_INTERFACE_NAME + " protocolReader, ").append(Util.JSON_READER_PARAMETER_DEF).append(")").openBlock();
          out.append("return protocolReader.").append(generator.naming.eventData.getParseMethodName(domainName, event.name())).append("(reader);").closeBlock();
        }
        out.closeBlock();
        out.semi();
      }
    });
    fileUpdater.update();
  }

  private void generateJsonProtocolInterface(TextOutput out, String className, String description, List<ProtocolMetaModel.Parameter> parameters, TextOutConsumer additionalMembersText) {
    if (description != null) {
      out.doc(description);
    }
    out.append("@org.jetbrains.jsonProtocol.JsonType").newLine().append("public interface ").append(className).openBlock();
    InputClassScope classScope = new InputClassScope(this, new NamePath(className, new NamePath(ClassNameScheme.getPackageName(generator.naming.inputPackage, domain.domain()))));
    if (additionalMembersText != null) {
      classScope.addMember(additionalMembersText);
    }
    if (parameters != null) {
      classScope.generateDeclarationBody(out, parameters);
    }
    classScope.writeAdditionalMembers(out);
    out.closeBlock();
  }
}