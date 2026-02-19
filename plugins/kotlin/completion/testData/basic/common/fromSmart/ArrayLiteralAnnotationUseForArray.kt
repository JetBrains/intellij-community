annotation class Foo(val a: Array<String>)

@Foo(<caret>) fun foo() {}

// IGNORE_K2
// EXIST: "[]"
// LANGUAGE_VERSION: 1.2