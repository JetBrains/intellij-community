// "Change exception object to class" "true"
// WITH_STDLIB
package some

import some.MyException

<caret>object MyException : Throwable()

fun foo() {
    throw MyException
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix