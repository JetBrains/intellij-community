// "Add 'operator' modifier" "true"
class A {
    fun contains(x: Any): Boolean = false
}

fun foo() = 0 i<caret>n A()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix