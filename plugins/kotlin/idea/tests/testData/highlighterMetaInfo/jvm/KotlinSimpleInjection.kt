// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package language_injection

import org.intellij.lang.annotations.Language

@Language("kotlin")
val test = "fun test2() {}"
