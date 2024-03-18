// EXPECTED_DUPLICATED_HIGHLIGHTING
package testing

<symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">enum</symbolName> class <symbolName textAttributesKey="KOTLIN_ENUM">Test</symbolName> {
    <symbolName textAttributesKey="KOTLIN_ENUM_ENTRY">FIRST</symbolName>,
    <symbolName textAttributesKey="KOTLIN_ENUM_ENTRY">SECOND</symbolName>
}

<symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">enum</symbolName> class <symbolName textAttributesKey="KOTLIN_ENUM">Type</symbolName>(val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">id</symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>) {
    <symbolName textAttributesKey="KOTLIN_ENUM_ENTRY">FIRST</symbolName>(1),
    <symbolName textAttributesKey="KOTLIN_ENUM_ENTRY">SECOND</symbolName>(2)
}

fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">testing</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">t1</symbolName>: <symbolName textAttributesKey="KOTLIN_ENUM">Test</symbolName>, <symbolName textAttributesKey="KOTLIN_PARAMETER">t2</symbolName>: <symbolName textAttributesKey="KOTLIN_ENUM">Test</symbolName>): <symbolName textAttributesKey="KOTLIN_ENUM">Test</symbolName> {
    if (<symbolName textAttributesKey="KOTLIN_PARAMETER">t1</symbolName> != <symbolName textAttributesKey="KOTLIN_PARAMETER">t2</symbolName>) return <symbolName textAttributesKey="KOTLIN_ENUM">Test</symbolName>.<symbolName textAttributesKey="KOTLIN_ENUM_ENTRY">FIRST</symbolName>
    return <symbolName textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">testing</symbolName>(<symbolName textAttributesKey="KOTLIN_ENUM">Test</symbolName>.<symbolName textAttributesKey="KOTLIN_ENUM_ENTRY">FIRST</symbolName>, <symbolName textAttributesKey="KOTLIN_ENUM">Test</symbolName>.<symbolName textAttributesKey="KOTLIN_ENUM_ENTRY">SECOND</symbolName>)
}