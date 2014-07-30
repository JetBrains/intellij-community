package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.ItemDescriptor.Named;
import org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty;

import java.util.List;

class InputClassScope extends ClassScope {
  InputClassScope(DomainGenerator generator, NamePath namePath) {
    super(generator, namePath);
  }

  void generateDeclarationBody(@NotNull TextOutput out, @NotNull List<? extends Named> list) {
    for (int i = 0, n = list.size(); i < n; i++) {
      Named named = list.get(i);
      if (named.description() != null) {
        out.doc(named.description());
      }

      String name = getName(named);
      String declarationName = Generator.generateMethodNameSubstitute(name, out);
      TypeDescriptor typeDescriptor = new InputMemberScope(name).resolveType(named);
      typeDescriptor.writeAnnotations(out);
      out.append(typeDescriptor.getType().getShortText(getClassContextNamespace())).space().append(declarationName).append("();");
      if (i != (n - 1)) {
        out.newLine().newLine();
      }
    }
  }

  @Override
  protected TypeData.Direction getTypeDirection() {
    return TypeData.Direction.INPUT;
  }

  class InputMemberScope extends MemberScope {
    InputMemberScope(String memberName) {
      super(InputClassScope.this, memberName);
    }

    @Override
    public BoxableType generateEnum(final String description, final List<String> enumConstants) {
      final String enumName = Generator.capitalizeFirstChar(getMemberName());
      addMember(new TextOutConsumer() {
        @Override
        public void append(TextOutput out) {
          out.newLine().doc(description);
          Enums.appendEnums(enumConstants, enumName, true, out);
        }
      });
      return new StandaloneType(new NamePath(enumName, getClassContextNamespace()), "writeEnum");
    }

    @Override
    public BoxableType generateNestedObject(final String description, final List<ObjectProperty> propertyList) {
      final String objectName = Generator.capitalizeFirstChar(getMemberName());
      addMember(new TextOutConsumer() {
        @Override
        public void append(TextOutput out) {
          out.newLine().doc(description);
          if (propertyList == null) {
            out.append("@org.jetbrains.jsonProtocol.JsonType(allowsOtherProperties=true)").newLine();
            out.append("public interface ").append(objectName).append(" extends org.jetbrains.jsonProtocol.JsonObjectBased").openBlock();
          }
          else {
            out.append("@org.jetbrains.jsonProtocol.JsonType").newLine();
            out.append("public interface ").append(objectName).openBlock();
            for (ObjectProperty property : propertyList) {
              out.doc(property.description());

              String methodName = Generator.generateMethodNameSubstitute(getName(property), out);
              MemberScope memberScope = new InputMemberScope(getName(property));
              TypeDescriptor propertyTypeData = memberScope.resolveType(property);
              propertyTypeData.writeAnnotations(out);

              out.append(propertyTypeData.getType().getShortText(getClassContextNamespace()) + ' ' + methodName + "();").newLine();
            }
          }
          out.closeBlock();
        }
      });
      return new StandaloneType(new NamePath(objectName, getClassContextNamespace()), "writeMessage");
    }
  }
}
