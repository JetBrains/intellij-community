// WITH_STDLIB
// EXPECTED_DUPLICATED_HIGHLIGHTING
fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">global</symbolName>() {
    fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">inner</symbolName>() {

    }
    <symbolName textAttributesKey="KOTLIN_FUNCTION_CALL">inner</symbolName>()
}

fun <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>.<symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">ext</symbolName>() {
}

<symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">infix</symbolName> fun <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>.<symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">fif</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">y</symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>) {
    this * <symbolName textAttributesKey="KOTLIN_PARAMETER">y</symbolName>
}

<symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">open</symbolName> class <symbolName textAttributesKey="KOTLIN_CLASS">Container</symbolName> {
    <symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">open</symbolName> fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">member</symbolName>() {
        <symbolName textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">global</symbolName>()
        5.<symbolName textAttributesKey="KOTLIN_EXTENSION_FUNCTION_CALL">ext</symbolName>()
        <symbolName textAttributesKey="KOTLIN_FUNCTION_CALL">member</symbolName>()
        5 <symbolName textAttributesKey="KOTLIN_EXTENSION_FUNCTION_CALL">fif</symbolName> 6
    }
}

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() {
    <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">suspend</symbolName> {

    }
}
