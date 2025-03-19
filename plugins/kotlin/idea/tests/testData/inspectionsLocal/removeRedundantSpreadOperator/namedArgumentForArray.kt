// ERROR: Assigning single elements to varargs in named form is forbidden
// ERROR: Type mismatch: inferred type is String but Array<out TypeVariable(T)> was expected

fun foo(vararg x: String) {}

fun bar() {
    foo(<caret>*arrayOf(elements = "abc"))
}
