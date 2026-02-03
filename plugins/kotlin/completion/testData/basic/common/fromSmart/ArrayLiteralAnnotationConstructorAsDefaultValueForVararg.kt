annotation class Bar(vararg val a: String = <caret>)

// IGNORE_K2
// EXIST: "[]"
// LANGUAGE_VERSION: 1.2