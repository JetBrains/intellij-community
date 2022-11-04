// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// IGNORE_FIR
// TODO: remove "IGNORE_FIR" after KTIJ-23678
package com.jetbrains

enum class EnumClass

fun foo() {
    for (e in com.jetbrains.EnumClass.values<caret>()) { }
}