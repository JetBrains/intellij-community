// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

fun foo(@Language("kotlin") vararg s: String){}
fun bar(vararg s: String, @Language("kotlin") name: String){}

val f = foo("fun foo(){}","fun foo(){}","fun foo(){}")
val b = bar("fun foo(){}","fun foo(){}","fun foo(){}", name = "fun foo(){}")
