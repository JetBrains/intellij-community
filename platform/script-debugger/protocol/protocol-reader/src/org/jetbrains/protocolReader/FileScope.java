package org.jetbrains.protocolReader;

class FileScope extends GlobalScope {
  private final TextOutput out;

  FileScope(GlobalScope globalScope, StringBuilder stringBuilder) {
    super(globalScope);
    out = new TextOutput(stringBuilder);
  }

  FileScope(FileScope fileScope) {
    super(fileScope);
    out = fileScope.out;
  }

  public TextOutput getOutput() {
    return out;
  }

  public ClassScope newClassScope() {
    return new ClassScope(this, asClassScope());
  }

  protected ClassScope asClassScope() {
    return null;
  }
}