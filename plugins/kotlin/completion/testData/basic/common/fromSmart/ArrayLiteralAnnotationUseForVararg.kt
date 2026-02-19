annotation class Bar(vararg val a: String)

@Bar(*<caret>) fun bar() {}

// IGNORE_K2
// EXIST: "[]"
// LANGUAGE_VERSION: 1.2