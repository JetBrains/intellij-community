// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

fun kotlin(@Language("kotlin") code: String): String = code

const val FOO = "fun foo(){}"

val withConcat = kotlin((("""
    val x = 1
""")).trimIndent() + FOO)

