package org.jetbrains.protocolReader;

import org.jetbrains.jsonProtocol.ItemDescriptor;
import org.jetbrains.jsonProtocol.ProtocolMetaModel;

import java.util.List;

/**
 * Member scope is used to generate additional types that are used only from method.
 * These types will be named after this method.
 */
abstract class MemberScope implements ResolveAndGenerateScope {
  private final String memberName;
  private final ClassScope classScope;

  MemberScope(ClassScope classScope, String memberName) {
    this.classScope = classScope;
    this.memberName = memberName;
  }

  @Override
  public <T extends ItemDescriptor> QualifiedTypeData resolveType(T typedObject) {
    return classScope.generator.generator.resolveType(typedObject, this);
  }

  protected String getMemberName() {
    return memberName;
  }

  public abstract BoxableType generateEnum(String description, List<String> enumConstants);
  public abstract BoxableType generateNestedObject(String description, List<ProtocolMetaModel.ObjectProperty> propertyList);

  @Override
  public String getDomainName() {
    return classScope.generator.domain.domain();
  }

  @Override
  public TypeData.Direction getTypeDirection() {
    return classScope.getTypeDirection();
  }
}
