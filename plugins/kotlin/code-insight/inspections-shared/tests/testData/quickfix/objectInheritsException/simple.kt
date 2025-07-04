// "Change exception object to class" "true"
// WITH_STDLIB
<caret>object MyException : Exception()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ObjectInheritsExceptionInspection$ChangeObjectToClassQuickFix