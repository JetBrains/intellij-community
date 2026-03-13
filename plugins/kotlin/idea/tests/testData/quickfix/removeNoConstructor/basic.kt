// "Remove constructor call" "true"
// K2_ERROR: This type does not have a constructor.

interface Base
class Derived : Base()<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNoConstructorFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNoConstructorFix