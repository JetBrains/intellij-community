// "Add 'operator' modifier" "true"
// ERROR: 'operator' modifier is required on 'component2' in 'A'
// K2_AFTER_ERROR: 'operator' modifier is required on 'fun component2(): Int' defined in 'A'.

class A {
    fun component1(): Int = 0
    fun component2(): Int = 1
}

fun foo() {
    val (<caret>zero, one) = A()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix