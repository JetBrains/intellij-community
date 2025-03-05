# Syntax Library

IntelliJ Platform Syntax Library provides an engine for parsing files.
See [Syntax PSI library](../syntax-psi/README.md) for more information on how to build PSI tree

# Main concepts

`com.intellij.platform.syntax.SyntaxElementType` represents a type of a node in the syntax tree (both for leafs and composites).

`com.intellij.platform.syntax.parser.SyntaxTreeBuilder` allows parsing the tree. 
You can obtain an instance of `SyntaxTreeBuilder` with the help of `com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory`.

`com.intellij.platform.syntax.parser.ProductionResultKt.prepareProduction` allows to obtain the raw result of parsing.

`com.intellij.platform.syntax.lexer.Lexer` provides lexing functionality.

# Example of usage

```kotlin
val builder = SyntaxTreeBuilderFactory.builder(
  text = "my file text", 
  whitespaces = setOf(SyntaxTokenTypes.WHITE_SPACE),
  comments = emptySet(),
  lexer = myLexer
)
val marker = builder.mark()
parseMyFileContent()
marker.done(type = MyLangSyntaxElementTypes.FILE)

val production = prepareProduction(builder = builder)
```
