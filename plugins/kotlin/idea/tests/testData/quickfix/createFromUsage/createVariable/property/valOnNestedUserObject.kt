// "Create member property 'A.FOO'" "true"
// K2_ACTION: "Create property 'FOO'" "true"
// ERROR: Property must be initialized or be abstract
// K2_AFTER_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT
// K2_ERROR: UNRESOLVED_REFERENCE

fun foo(){

    A.B.C.F<caret>OO

}
object A {
    object B {
        object C
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction