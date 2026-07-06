// "Import extension function 'C.extension'" "true"
// WITH_STDLIB
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

class A
class B
class C

object AExtObject {
    fun A.extension() {}
}

object BExtObject {
    fun B.extension() {}
}

object CExtObject {
    fun C.extension() {}
}

fun usage(a: A, b: B, c: C) {
    a.run { b.run { c.<caret>extension() } }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix