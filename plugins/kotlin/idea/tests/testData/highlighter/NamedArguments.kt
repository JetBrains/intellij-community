// EXPECTED_DUPLICATED_HIGHLIGHTING
<symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@Suppress</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES">names =</symbolName> ["foo"])
fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">p1</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">p2</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>): <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName> {
    return <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">p2</symbolName> + <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">p1</symbolName>
}

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">bar</symbolName>() {
    <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">foo</symbolName>(1, <symbolName descr="null" textAttributesKey="KOTLIN_NAMED_ARGUMENT">p2 =</symbolName> "")
}