// LANGUAGE_VERSION: 1.8
// EXPECTED_DUPLICATED_HIGHLIGHTING
fun <<symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>> <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">x</symbolName>: <symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T & Any</symbolName>) : <symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T & Any</symbolName> {
    val <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">y</symbolName>: <symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T & Any</symbolName> = <symbolName textAttributesKey="KOTLIN_PARAMETER">x</symbolName>
    return <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">y</symbolName>
}
