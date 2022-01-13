// WITH_RUNTIME
// INTENTION_TEXT: "Add '@JvmOverloads' annotation to function 'foo'"
// AFTER-WARNING: Parameter 'a' is never used

fun foo(a: String = ""<caret>) {
}