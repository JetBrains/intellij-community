// "Change to constructor invocation" "true"

open class A {

}

class B : A<caret> {}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix