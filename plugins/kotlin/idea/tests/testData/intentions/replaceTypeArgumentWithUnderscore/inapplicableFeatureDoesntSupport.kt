// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -XXLanguage:-PartiallySpecifiedTypeArguments
// AFTER-WARNING: Parameter 't' is never used
// AFTER-WARNING: Variable 'x' is never used

fun foo() {
    val x = bar<<caret>String>("x")
}

fun <T> bar(t: T): Int = 1