// "Add 'operator' modifier" "true"
class A {
    fun plus(a: A): A = A()
}

fun foo() {
    A() <caret>+ A()
}

/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory$createAction$1