// ALLOW_ERRORS
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

fun <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>main<!>() {
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'AAA'.'; highlightingTextAttributesKey='WRONG_REFERENCES_ATTRIBUTES'")!>AAA<!>()
}
