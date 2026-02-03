# intellij.platform.syntax.util

Utilities to be used in syntax implementations

## SyntaxGeneratedParserRuntime

Kotlin-multiplatform compatible runtime engine for parsers generated using the [Grammar-Kit](https://github.com/JetBrains/Grammar-Kit)

Example of use:

```kotlin
    @Override
    protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
      PsiSyntaxBuilderFactory builderFactory = PsiSyntaxBuilderFactory.getInstance();
      var elementType = chameleon.getElementType();
      var lexer = new JsonSyntaxLexer();
      var syntaxBuilder = builderFactory.createBuilder(chameleon, lexer, getLanguage(), chameleon.getChars());
      var treeBuilder = syntaxBuilder.getSyntaxTreeBuilder()
      var runtimeParserRuntime = new PsiSyntaxParserRuntimeFactoryImpl(JsonLanguage.INSTANCE)
                                       .buildParserUtils(treeBuilder);

      new JsonParser().parse(Objects.requireNonNull(ElementTypeConverters.getConverter(JsonLanguage)).convert(elementType), runtimeParserRuntime);
      return syntaxBuilder.getTreeBuilt().getFirstChildNode();
    }
```