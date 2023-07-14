// "Change to constructor invocation" "true"

open class A {

}

class B : A<caret> {}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix