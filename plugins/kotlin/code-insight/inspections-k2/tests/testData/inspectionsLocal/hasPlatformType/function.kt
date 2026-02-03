// WITH_STDLIB
// FIX: Specify return type explicitly

fun foo<caret>() = java.lang.String.valueOf(1)

// IGNORE_K2