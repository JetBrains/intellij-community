// EXPECTED_DUPLICATED_HIGHLIGHTING

interface <symbolName textAttributesKey="KOTLIN_TRAIT">FunctionLike</symbolName> {
    <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">operator</symbolName> fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">invoke</symbolName>() {
    }
}

var <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">global</symbolName></symbolName> : () -> <symbolName textAttributesKey="KOTLIN_OBJECT">Unit</symbolName> = {}

val <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>.<symbolName textAttributesKey="KOTLIN_EXTENSION_PROPERTY">ext</symbolName> : () -> <symbolName textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>
<symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() {
  return {}
}

fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">a</symbolName> : () -> <symbolName textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>, <symbolName textAttributesKey="KOTLIN_PARAMETER">functionLike</symbolName>: <symbolName textAttributesKey="KOTLIN_TRAIT">FunctionLike</symbolName>) {
    <symbolName textAttributesKey="KOTLIN_PARAMETER"><symbolName textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">a</symbolName></symbolName>()
    <symbolName textAttributesKey="KOTLIN_PARAMETER"><symbolName textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION_LIKE">functionLike</symbolName></symbolName>()
    <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">global</symbolName></symbolName></symbolName>()
    1.<symbolName textAttributesKey="KOTLIN_EXTENSION_PROPERTY"><symbolName textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">ext</symbolName></symbolName>();

    {}() //should not be highlighted as "calling variable as function"!
}
