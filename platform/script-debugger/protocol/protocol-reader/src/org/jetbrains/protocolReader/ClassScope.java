package org.jetbrains.protocolReader;

public class ClassScope extends FileScope {
  private final ClassScope parentClass;

  public ClassScope(FileScope fileScope, ClassScope parentClass) {
    super(fileScope);
    this.parentClass = parentClass;
  }

  public ClassScope getRootClassScope() {
    if (parentClass == null) {
      return this;
    }
    else {
      return parentClass.getRootClassScope();
    }
  }

  @Override
  protected ClassScope asClassScope() {
    return this;
  }
}