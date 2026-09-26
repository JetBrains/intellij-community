// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.Language;

// Enum constructor parameter - should NOT be flagged
enum TokenType {
  IDENTIFIER(new IElementType("IDENTIFIER", Language.ANY)),
  KEYWORD(new IElementType("KEYWORD", Language.ANY));

  private final IElementType elementType;

  TokenType(IElementType elementType) {
    this.elementType = elementType;
  }

  public IElementType getElementType() {
    return elementType;
  }
}

// Enum body field - should NOT be flagged
enum ComplexTokenType {
  STRING, NUMBER;

  private final IElementType type = new IElementType("TYPE", Language.ANY);

  public IElementType getType() {
    return type;
  }
}

// Regular class field - should STILL be flagged (regression check)
class RegularClass {
  private final IElementType <warning descr="Suspicious instance field with IElementType initializer. Consider replacing it with a constant">type</warning> = new IElementType("TYPE", Language.ANY);
}
