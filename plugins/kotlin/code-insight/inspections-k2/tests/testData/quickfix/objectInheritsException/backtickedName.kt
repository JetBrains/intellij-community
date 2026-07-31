// "Change exception object to class" "true"

<caret>object `Foo-Bar` : Throwable()

fun foo() {
    throw `Foo-Bar`
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix