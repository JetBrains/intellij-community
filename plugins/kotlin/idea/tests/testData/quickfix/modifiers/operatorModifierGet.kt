// "Add 'operator' modifier" "true"
class A {
    fun get(i: Int): String = ""
}

fun foo() = A()<caret>[0]

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix