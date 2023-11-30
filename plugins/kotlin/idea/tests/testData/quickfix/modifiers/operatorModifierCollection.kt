// "Add 'operator' modifier" "true"
class A {
    fun contains(x: Any): Boolean = false
}

fun foo() = 0 i<caret>n A()

/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory$createAction$1