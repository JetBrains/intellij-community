package org.jetbrains.protocolReader;

class ExistingSubtypeAspect extends SubtypeAspect {
  private SubtypeCaster subtypeCaster;
  private final TypeRef<?> jsonSuperClass;

  ExistingSubtypeAspect(TypeRef<?> jsonSuperClass) {
    this.jsonSuperClass = jsonSuperClass;
  }

  public void setSubtypeCaster(SubtypeCaster subtypeCaster) {
    this.subtypeCaster = subtypeCaster;
  }

  @Override
  void writeGetSuperMethodJava(TextOutput out) {
    out.newLine().append("@Override").newLine().append("public ").append(jsonSuperClass.get().getTypeClass().getCanonicalName() ).append(" getSuper()").openBlock();
    out.append("return ").append(Util.BASE_VALUE_PREFIX).semi().closeBlock();
  }

  @Override
  void writeSuperFieldJava(TextOutput out) {
    out.newLine().append("private final ").append(jsonSuperClass.get().getTypeClass().getCanonicalName()).append(' ').append(Util.BASE_VALUE_PREFIX).semi().newLine();
  }

  @Override
  void writeSuperConstructorParamJava(TextOutput out) {
    out.comma().append(jsonSuperClass.get().getTypeClass().getCanonicalName()).append(' ').append(Util.BASE_VALUE_PREFIX);
  }

  @Override
  void writeSuperConstructorInitialization(TextOutput out) {
    out.append("this.").append(Util.BASE_VALUE_PREFIX).append(" = ").append(Util.BASE_VALUE_PREFIX).append(';').newLine().newLine();
  }

  @Override
  void writeParseMethod(String className, ClassScope scope, TextOutput out) {
    out.newLine().append("public static ").append(className).space().append("parse").append("(").append(Util.JSON_READER_PARAMETER_DEF).append(')').openBlock();
    out.append("return ");
    jsonSuperClass.get().writeInstantiateCode(scope, out);
    out.append('(').append(Util.READER_NAME).append(')').append('.');
    subtypeCaster.writeJava(out);
    out.semi().closeBlock();
    out.newLine();
  }

  @Override
  public void writeInstantiateCode(String className, TextOutput out) {
    out.append(className).append(".parse");
  }
}
