// "Import infix extension function 'Int.ext'" "true"
package p

open class A {
    infix fun Int.ext(other: Int) {}
}

object AObject : A()

fun usage() {
    10 <caret>ext 20
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix
// IGNORE_K1
