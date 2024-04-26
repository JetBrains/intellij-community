// EXPECTED_DUPLICATED_HIGHLIGHTING
package testing

object <symbolName textAttributesKey="KOTLIN_OBJECT">O</symbolName> {
    fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() = 42
}

fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">testing</symbolName>(): <symbolName textAttributesKey="KOTLIN_OBJECT">O</symbolName> {
    <symbolName textAttributesKey="KOTLIN_OBJECT">O</symbolName>.<symbolName textAttributesKey="KOTLIN_FUNCTION_CALL">foo</symbolName>()
    val <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">o</symbolName> = <symbolName textAttributesKey="KOTLIN_OBJECT">O</symbolName>
    <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">o</symbolName>.<symbolName textAttributesKey="KOTLIN_FUNCTION_CALL">foo</symbolName>()
    return <symbolName textAttributesKey="KOTLIN_OBJECT">O</symbolName>
}
