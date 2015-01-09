package org.jetbrains.protocolReader;

public class ClassScope extends FileScope {
  private final ClassScope parentClass;

  public ClassScope(FileScope fileScope, ClassScope parentClass) {
    super(fileScope);
    this.parentClass = parentClass;
  }

  public ClassScope getRootClassScope() {
    return parentClass == null ? this : parentClass.getRootClassScope();
  }

  @Override
  protected ClassScope asClassScope() {
    return this;
  }
}