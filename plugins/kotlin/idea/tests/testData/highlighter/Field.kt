// EXPECTED_DUPLICATED_HIGHLIGHTING
// FALSE_POSITIVE

var <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">my</symbolName></symbolName> = 0
    <symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() = <symbolName textAttributesKey="KOTLIN_BACKING_FIELD_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">field</symbolName></symbolName>
    <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">arg</symbolName>) {
        <symbolName textAttributesKey="KOTLIN_BACKING_FIELD_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">field</symbolName></symbolName> = <symbolName textAttributesKey="KOTLIN_PARAMETER">arg</symbolName> + 1
    }

fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(): <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> {
    val <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">field</symbolName> = <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">my</symbolName></symbolName>
    return <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">field</symbolName>
}