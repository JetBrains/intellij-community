annotation class Foo(val a: Array<String> = <caret>)

// IGNORE_K2
// EXIST: "[]"
// LANGUAGE_VERSION: 1.2