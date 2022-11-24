// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB

package com.jetbrains

enum class EnumClass

fun foo() {
    for (e in com.jetbrains.EnumClass.values<caret>()) { }
}