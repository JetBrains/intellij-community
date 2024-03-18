// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
// IGNORE_K2
package com.example

import com.example.EnumClass
import com.example.EnumClass as EnumClassImportAlias

enum class EnumClass

fun foo() {
    // Preserving original import alias after quick fix is only supported in K2
    EnumClassImportAlias.values<caret>()
}