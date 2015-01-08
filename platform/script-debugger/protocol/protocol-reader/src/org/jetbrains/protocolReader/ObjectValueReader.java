package org.jetbrains.protocolReader;

class ObjectValueReader<T> extends ValueReader {
  private final TypeRef<T> refToType;
  private final boolean isSubtyping;

  ObjectValueReader(TypeRef<T> refToType, boolean isSubtyping) {
    super();

    this.refToType = refToType;
    this.isSubtyping = isSubtyping;
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
  public void appendFinishedValueTypeName(TextOutput out) {
    out.append(refToType.getTypeClass().getCanonicalName());
  }

  @Override
  public void appendInternalValueTypeName(FileScope classScope, TextOutput out) {
    out.append(classScope.getTypeImplReference(refToType.get()));
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, String fieldName, TextOutput out) {
    refToType.get().writeInstantiateCode(scope.getRootClassScope(), subtyping, out);
    out.append('(');
    addReaderParameter(subtyping, out);
    out.comma().append("null");
    if (subtyping && refToType.get().getSubtypeSupport() != null) {
      out.comma().append("this");
    }
    out.append(')');
  }

  @Override
  public void writeArrayReadCode(ClassScope scope, boolean subtyping, TextOutput out) {
    beginReadCall("ObjectArray", subtyping, out);
    out.comma().append("new ").append(scope.requireFactoryGenerationAndGetName(refToType.get())).append(Util.TYPE_FACTORY_NAME_POSTFIX).append("()");
    out.append(')');
  }
}
