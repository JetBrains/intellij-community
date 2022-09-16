// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// DISABLE-ERRORS
sealed class Bar

data <caret>class Foo : Bar()
