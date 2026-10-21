// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

fun kotlin(@Language("kotlin") code: String): String = code

val withConcat1 = kotlin((("""
    val x = 1
""")).trimIndent())

