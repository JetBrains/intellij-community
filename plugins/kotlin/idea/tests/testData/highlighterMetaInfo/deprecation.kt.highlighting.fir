// HIGHLIGHTER_ATTRIBUTES_KEY
// HIGHLIGHT_WARNINGS

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'; highlightingTextAttributesKey='INFORMATION_ATTRIBUTES'")!>main<!>() {
    <!HIGHLIGHTING("severity='WARNING'; descr='[DEPRECATION] '@Deprecated(...) fun aaa(): Unit' is deprecated. deprecated'; highlightingTextAttributesKey='DEPRECATED_ATTRIBUTES'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'; highlightingTextAttributesKey='INFORMATION_ATTRIBUTES'")!>aaa<!>()
}

<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'; highlightingTextAttributesKey='INFORMATION_ATTRIBUTES'")!>@Deprecated<!>("deprecated")
fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'; highlightingTextAttributesKey='INFORMATION_ATTRIBUTES'")!>aaa<!>(){}
