// "Change exception object to class" "true"
// WITH_STDLIB
package p

<caret>object Obj : Exception() {
    fun foo() {
    }
}

fun test() {
    Obj.foo() // -> Obj.foo()
    p.Obj.foo() // -> p.Obj().foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix