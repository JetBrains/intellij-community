// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
import org.intellij.lang.annotations.Language

fun kotlin(@Language("kotlin") code: String): String = code

const val HASH = "#"

val withConcat = kotlin(("""
    #val x = 1
""".trimMargin(HASH)))