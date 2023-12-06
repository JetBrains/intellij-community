// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
package com.jetbrains

enum class EnumClass

fun foo() {
    for (e in com.jetbrains.EnumClass.values<caret>()) { }
}