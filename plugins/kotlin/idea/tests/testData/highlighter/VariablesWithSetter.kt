// EXPECTED_DUPLICATED_HIGHLIGHTING

<symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">open</symbolName> class <symbolName textAttributesKey="KOTLIN_CLASS">C</symbolName> {
    var <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">foo</symbolName></symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> = 0
    <symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">private</symbolName> <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>
    var <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">bar</symbolName></symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> = 0
    <symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">protected</symbolName> <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>
    var <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">baz</symbolName></symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> = 0
    <symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">internal</symbolName> <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>
    var <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">qux</symbolName></symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> = 0
    <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>
}

fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">test</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">c</symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">C</symbolName>) {
    <symbolName textAttributesKey="KOTLIN_PARAMETER">c</symbolName>.<symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">foo</symbolName>
    <symbolName textAttributesKey="KOTLIN_PARAMETER">c</symbolName>.<symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">bar</symbolName>
    <symbolName textAttributesKey="KOTLIN_PARAMETER">c</symbolName>.<symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">baz</symbolName></symbolName>
    <symbolName textAttributesKey="KOTLIN_PARAMETER">c</symbolName>.<symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">qux</symbolName></symbolName>
}

class <symbolName textAttributesKey="KOTLIN_CLASS">Test</symbolName> : <symbolName textAttributesKey="KOTLIN_CONSTRUCTOR">C</symbolName>() {
    fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">test</symbolName>() {
        <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">foo</symbolName>
        <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">bar</symbolName></symbolName>
        <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">baz</symbolName></symbolName>
        <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">qux</symbolName></symbolName>
    }
}
