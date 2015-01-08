package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class ExistingSubtypeAspect {
  private SubtypeCaster subtypeCaster;
  private final TypeRef<?> jsonSuperClass;

  ExistingSubtypeAspect(TypeRef<?> jsonSuperClass) {
    this.jsonSuperClass = jsonSuperClass;
  }

  public void setSubtypeCaster(SubtypeCaster subtypeCaster) {
    this.subtypeCaster = subtypeCaster;
  }

  void writeGetSuperMethodJava(@NotNull TextOutput out) {
    out.newLine().append("@Override").newLine().append("public ").append(jsonSuperClass.type.typeClass.getCanonicalName() ).append(" getSuper()").openBlock();
    out.append("return ").append(Util.BASE_VALUE_PREFIX).semi().closeBlock();
  }

  void writeSuperFieldJava(@NotNull TextOutput out) {
    out.newLine().append("private final ").append(jsonSuperClass.type.typeClass.getCanonicalName()).append(' ').append(Util.BASE_VALUE_PREFIX).semi().newLine();
  }

  void writeSuperConstructorParamJava(@NotNull TextOutput out) {
    out.comma().append(jsonSuperClass.type.typeClass.getCanonicalName()).append(' ').append(Util.BASE_VALUE_PREFIX);
  }

  void writeSuperConstructorInitialization(@NotNull TextOutput out) {
    out.append("this.").append(Util.BASE_VALUE_PREFIX).append(" = ").append(Util.BASE_VALUE_PREFIX).append(';').newLine().newLine();
  }

  void writeParseMethod(@NotNull String className, @NotNull ClassScope scope, @NotNull TextOutput out) {
    out.newLine().append("public static ").append(className).space().append("parse").append('(').append(Util.JSON_READER_PARAMETER_DEF).append(", String name").append(')').openBlock();
    out.append("return ");
    jsonSuperClass.type.writeInstantiateCode(scope, out);
    out.append('(').append(Util.READER_NAME).append(", name)").append('.');
    subtypeCaster.writeJava(out);
    out.semi().closeBlock();
    out.newLine();
  }

  public void writeInstantiateCode(@NotNull String className, @NotNull TextOutput out) {
    out.append(className).append(".parse");
  }
}
