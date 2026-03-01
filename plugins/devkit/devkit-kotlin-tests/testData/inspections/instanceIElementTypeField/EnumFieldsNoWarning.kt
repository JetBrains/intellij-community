// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.psi.tree.IElementType
import com.intellij.lang.Language

// Enum constructor parameter - should NOT be flagged
enum class TokenType(val elementType: IElementType) {
  IDENTIFIER(IElementType("IDENTIFIER", Language.ANY)),
  KEYWORD(IElementType("KEYWORD", Language.ANY))
}

// Enum with body field - should NOT be flagged
enum class ComplexTokenType {
  STRING, NUMBER;

  private val type = IElementType("TYPE", Language.ANY)

  fun getType() = type
}

// Enum with companion object static field - should NOT be flagged (already handled by companion check)
enum class TokenWithStatic {
  VALUE;

  companion object {
    val STATIC_TYPE = IElementType("STATIC", Language.ANY)
  }
}

// Regular class - should STILL be flagged (regression check)
class RegularClass {
  private val <warning descr="Suspicious instance field with IElementType initializer. Consider replacing it with a constant">type</warning> = IElementType("TYPE", Language.ANY)
}
