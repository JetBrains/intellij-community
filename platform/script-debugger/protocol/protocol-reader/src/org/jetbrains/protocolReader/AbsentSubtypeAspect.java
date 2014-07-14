package org.jetbrains.protocolReader;

class AbsentSubtypeAspect extends SubtypeAspect {

  @Override
  void writeGetSuperMethodJava(TextOutput out) {
  }

  @Override
  void writeSuperFieldJava(TextOutput out) {
  }

  @Override
  void writeSuperConstructorParamJava(TextOutput out) {
  }

  @Override
  void writeSuperConstructorInitialization(TextOutput out) {
  }

  @Override
  void writeParseMethod(String className, ClassScope scope, TextOutput out) {
  }

  @Override
  public void writeInstantiateCode(String className, TextOutput out) {
    out.append("new ").append(className);
  }
}
