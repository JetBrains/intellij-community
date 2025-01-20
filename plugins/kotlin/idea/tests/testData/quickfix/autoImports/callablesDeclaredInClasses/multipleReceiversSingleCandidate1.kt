// "Import extension function 'C.extension'" "true"
// WITH_STDLIB
package p

class A
class B
class C

open class AExt {
    fun A.extension() {}
}

open class BExt {
    fun B.extension() {}
}

open class CExt {
    fun C.extension() {}
}

object AExtObject : AExt()
object BExtObject : BExt()
object CExtObject : CExt()

fun usage(a: A, b: B, c: C) {
    a.run { b.run { c.<caret>extension() } }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix