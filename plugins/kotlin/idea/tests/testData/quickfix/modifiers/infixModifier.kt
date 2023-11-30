// "Add 'infix' modifier" "true"
class A {
    fun xyzzy(i: Int) {}
}

fun foo() {
    A() xyz<caret>zy 5
}
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory$createAction$1