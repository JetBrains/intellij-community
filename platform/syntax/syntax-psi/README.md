# Syntax PSI Library

This library produces PSI trees with the help of `intellij.platform.syntax` library.

# Main concepts

`com.intellij.platform.syntax.psi.LanguageSyntaxDefinition` is an extension point allowing to
specify some common matters for your language.

`com.intellij.platform.syntax.psi.PsiSyntaxBuilder` gives an API for building a PSI tree.

`com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory` allows to create a `PsiSyntaxBuilder`.

`com.intellij.platform.syntax.psi.ElementTypeConverter` provides mapping between your `com.intellij.psi.tree.IElementType`
and `com.intellij.platform.syntax.SyntaxElementType`.

# Example of usage

```kotlin

class MyCodeBlockElementType : ILazyParseableElementType {
  override fun parseContents(chameleon: ASTNode): ASTNode {
    val builder = PsiSyntaxBuilderFactory.getInstance().createBuilder(chameleon, myLexer, myLanguage, chameleon.chars)
    MyParser().parse(builder.getSyntaxTreeBuilder())
    return builder.getTreeBuilt().getFirstChildNode()
  }
}
```