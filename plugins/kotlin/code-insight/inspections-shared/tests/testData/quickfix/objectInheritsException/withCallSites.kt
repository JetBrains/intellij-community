// "Change exception object to class" "true"
// WITH_STDLIB
package some

import some.MyException

<caret>object MyException : Throwable()

fun foo() {
    throw MyException
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix