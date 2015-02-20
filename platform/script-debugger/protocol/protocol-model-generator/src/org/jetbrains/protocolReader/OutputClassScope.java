package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.jsonProtocol.ItemDescriptor;
import org.jetbrains.jsonProtocol.ProtocolMetaModel;

import java.util.ArrayList;
import java.util.List;

class OutputClassScope extends ClassScope {
  OutputClassScope(@NotNull DomainGenerator generator, @NotNull NamePath classNamePath) {
    super(generator, classNamePath);
  }

  <P extends ItemDescriptor.Named> void generate(@NotNull TextOutput out, @Nullable List<P> parameters) {
    if (parameters == null) {
      return;
    }

    List<P> mandatoryParameters = new ArrayList<>();
    List<P> optionalParameters = new ArrayList<>();
    for (P parameter : parameters) {
      if (parameter.optional()) {
        optionalParameters.add(parameter);
      }
      else {
        mandatoryParameters.add(parameter);
      }
    }

    if (!mandatoryParameters.isEmpty()) {
      generateConstructor(out, mandatoryParameters, null);
      if (mandatoryParameters.size() == 1) {
        P parameter = mandatoryParameters.get(0);
        TypeDescriptor typeData = new OutputMemberScope(getName(parameter)).resolveType(parameter);
        if (typeData.getType().getFullText().equals("int[]")) {
          BoxableType[] types = new BoxableType[mandatoryParameters.size()];
          types[0] = new ListType(BoxableType.INT) {
            @Override
            String getShortText(NamePath contextNamespace) {
              return getFullText();
            }

            @Override
            String getFullText() {
              return "gnu.trove.TIntArrayList";
            }

            @Override
            String getWriteMethodName() {
              return "writeIntList";
            }
          };

          out.newLine().newLine();
          generateConstructor(out, mandatoryParameters, types);

          types[0] = new ListType(BoxableType.INT) {
            @Override
            String getShortText(NamePath contextNamespace) {
              return getFullText();
            }

            @Override
            String getFullText() {
              return "int";
            }

            @Override
            String getWriteMethodName() {
              return "writeSingletonIntArray";
            }
          };

          out.newLine().newLine();
          generateConstructor(out, mandatoryParameters, types);
        }
      }
    }

    // generate enum classes after constructor
    for (P parameter : parameters) {
      if (parameter.getEnum() != null) {
        out.newLine().newLine();
        appendEnumClass(out, parameter.description(), parameter.getEnum(), Generator.capitalizeFirstChar((parameter.name())));
      }
    }

    for (P parameter : optionalParameters) {
      out.newLine().newLine();
      if (parameter.description() != null) {
        out.append("/**").newLine().append(" * @param v ").append(parameter.description()).newLine().append(" */").newLine();
      }

      CharSequence type = new OutputMemberScope(parameter.name()).resolveType(parameter).getType().getShortText(getClassContextNamespace());
      if (type.equals(JsonReaderEx.class.getCanonicalName())) {
        type = "String";
      }

      out.append("public ").append(getShortClassName());
      out.space().append(parameter.name()).append("(").append(type);
      out.space().append("v").append(")").openBlock();
      appendWriteValueInvocation(out, parameter, "v", null);
      out.newLine().append("return this;");
      out.closeBlock();
    }
  }

  private <P extends ItemDescriptor.Named> void generateConstructor(@NotNull TextOutput out, @NotNull List<P> parameters, @Nullable BoxableType[] parameterTypes) {
    boolean hasDoc = false;
    for (P parameter : parameters) {
      if (parameter.description() != null) {
        hasDoc = true;
        break;
      }
    }
    if (hasDoc) {
      out.append("/**").newLine();
      for (P parameter : parameters) {
        if (parameter.description() != null) {
          out.append(" * @param " + parameter.name() + ' ' + parameter.description()).newLine();
        }
      }
      out.append(" */").newLine();
    }
    out.append("public " + getShortClassName() + '(');

    if (parameterTypes == null) {
      parameterTypes = new BoxableType[parameters.size()];
    }
    writeMethodParameters(out, parameters, parameterTypes);
    out.append(')');

    out.openBlock(false);
    writeWriteCalls(out, parameters, parameterTypes, null);
    out.closeBlock();
  }

  <P extends ItemDescriptor.Named> void writeWriteCalls(@NotNull TextOutput out, @NotNull List<P> parameters, @NotNull BoxableType[] parameterTypes, @Nullable String qualifier) {
    for (int i = 0, size = parameters.size(); i < size; i++) {
      out.newLine();

      if (qualifier != null) {
        out.append(qualifier).append('.');
      }

      P parameter = parameters.get(i);
      appendWriteValueInvocation(out, parameter, parameter.name(), parameterTypes[i]);
    }
  }

  <P extends ItemDescriptor.Named> void writeMethodParameters(@NotNull TextOutput out, @NotNull List<P> parameters, @NotNull BoxableType[] parameterTypes) {
    for (int i = 0, length = parameterTypes.length; i < length; i++) {
      if (parameterTypes[i] == null) {
        P parameter = parameters.get(i);
        parameterTypes[i] = new OutputMemberScope(parameter.name()).resolveType(parameter).getType();
      }
    }

    boolean needComa = false;
    for (int i = 0, size = parameters.size(); i < size; i++) {
      if (needComa) {
        out.comma();
      }
      else {
        needComa = true;
      }

      out.append(parameterTypes[i].getShortText(getClassContextNamespace()));
      out.space().append(parameters.get(i).name());
    }
  }

  private void appendWriteValueInvocation(TextOutput out, ItemDescriptor.Named parameter, String valueRefName, @Nullable BoxableType type) {
    if (type == null) {
      type = new OutputMemberScope(parameter.name()).resolveType(parameter).getType();
    }

    boolean blockOpened = false;
    if (parameter.optional()) {
      String nullValue;
      if (parameter.name().equals("columnNumber") || parameter.name().equals("column")) {
        // todo generic solution
        nullValue = "-1";
      }
      else {
        nullValue = null;
      }

      if (nullValue != null) {
        blockOpened = true;
        out.append("if (v != ").append(nullValue).append(")").openBlock();
      }
      else if (parameter.name().equals("enabled")) {
        blockOpened = true;
        out.append("if (!v)").openBlock();
      }
      else if (parameter.name().equals("ignoreCount")) {
        blockOpened = true;
        out.append("if (v > 0)").openBlock();
      }
    }
    // todo CallArgument (we should allow write null as value)
    out.append(parameter.name().equals("value") && type.getWriteMethodName().equals("writeString") ? "writeNullableString" : type.getWriteMethodName()).append("(");
    out.quote(parameter.name()).comma().append(valueRefName).append(");");
    if (blockOpened) {
      out.closeBlock();
    }
  }

  @Override
  protected TypeData.Direction getTypeDirection() {
    return TypeData.Direction.OUTPUT;
  }

  class OutputMemberScope extends MemberScope {
    protected OutputMemberScope(String memberName) {
      super(OutputClassScope.this, memberName);
    }

    @Override
    public BoxableType generateEnum(final String description, final List<String> enumConstants) {
      return new StandaloneType(new NamePath(Generator.capitalizeFirstChar(getMemberName()), getClassContextNamespace()), "writeEnum");
    }

    @Override
    public BoxableType generateNestedObject(String description, List<ProtocolMetaModel.ObjectProperty> propertyList) {
      throw new UnsupportedOperationException();
    }
  }

  private static void appendEnumClass(TextOutput out, String description, List<String> enumConstants, String enumName) {
    out.doc(description);
    Enums.appendEnums(enumConstants, enumName, false, out);
    out.newLine().append("private final String protocolValue;").newLine();
    out.newLine().append(enumName).append("(String protocolValue)").openBlock();
    out.append("this.protocolValue = protocolValue;").closeBlock();

    out.newLine().newLine().append("public String toString()").openBlock();
    out.append("return protocolValue;").closeBlock();
    out.closeBlock();
  }
}
