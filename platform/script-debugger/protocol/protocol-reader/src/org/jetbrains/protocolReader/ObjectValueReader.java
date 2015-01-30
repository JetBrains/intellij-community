package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ObjectValueReader<T> extends ValueReader {
  private final TypeRef<T> refToType;
  private final boolean isSubtyping;
  final String primitiveValueName;

  ObjectValueReader(@NotNull TypeRef<T> refToType, boolean isSubtyping, @Nullable String primitiveValueName) {
    super();

    this.refToType = refToType;
    this.isSubtyping = isSubtyping;
    this.primitiveValueName = primitiveValueName == null || primitiveValueName.isEmpty() ? null : primitiveValueName;
  }

  TypeRef<T> getType() {
    return refToType;
  }

  @Override
  public ObjectValueReader asJsonTypeParser() {
    return this;
  }

  public boolean isSubtyping() {
    return isSubtyping;
  }

  @Override
  public void appendFinishedValueTypeName(@NotNull TextOutput out) {
    out.append(refToType.typeClass.getCanonicalName());
  }

  @Override
  public void appendInternalValueTypeName(@NotNull FileScope classScope, @NotNull TextOutput out) {
    out.append(classScope.getTypeImplReference(refToType.type));
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
    refToType.type.writeInstantiateCode(scope.getRootClassScope(), subtyping, out);
    out.append('(');
    addReaderParameter(subtyping, out);
    out.comma().append("null");
    if (subtyping && refToType.type.subtypeAspect != null) {
      out.comma().append("this");
    }
    out.append(')');
  }

  @Override
  public void writeArrayReadCode(@NotNull ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
    beginReadCall("ObjectArray", subtyping, out);
    writeFactoryArgument(scope, out);
    out.append(')');
  }

  void writeFactoryArgument(@NotNull ClassScope scope, @NotNull TextOutput out) {
    out.comma();
    writeFactoryNewExpression(scope, out);
  }

  void writeFactoryNewExpression(@NotNull ClassScope scope, @NotNull TextOutput out) {
    out.append("new ").append(scope.requireFactoryGenerationAndGetName(refToType.type)).append(Util.TYPE_FACTORY_NAME_POSTFIX).append("()");
  }
}
