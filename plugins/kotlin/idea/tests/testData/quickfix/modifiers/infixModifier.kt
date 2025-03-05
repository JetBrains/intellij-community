// "Add 'infix' modifier" "true"
class A {
    fun xyzzy(i: Int) {}
}

fun foo() {
    A() xyz<caret>zy 5
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix