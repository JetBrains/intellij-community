// "Add parameter to function 'bar'" "true"
// WITH_STDLIB
// DISABLE-ERRORS
// COMPILER_ARGUMENTS: -XXLanguage:-NewInference
private val foo = Foo().bar(1, "2", <caret>setOf("3"))