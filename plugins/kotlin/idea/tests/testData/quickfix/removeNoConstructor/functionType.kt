// "Remove constructor call" "true"

abstract class A : () -> Int()<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNoConstructorFix