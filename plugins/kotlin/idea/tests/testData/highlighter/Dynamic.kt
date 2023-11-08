// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING
package testing

fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">tst</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">d</symbolName>: <error><symbolName>dynamic</symbolName></error>) {
    <symbolName textAttributesKey="KOTLIN_PARAMETER">d</symbolName>.<symbolName textAttributesKey="KOTLIN_DYNAMIC_FUNCTION_CALL">foo</symbolName>()
    <symbolName textAttributesKey="KOTLIN_PARAMETER">d</symbolName>.<symbolName textAttributesKey="KOTLIN_DYNAMIC_PROPERTY_CALL">foo</symbolName>
    <symbolName textAttributesKey="KOTLIN_PARAMETER">d</symbolName>.<symbolName textAttributesKey="KOTLIN_DYNAMIC_PROPERTY_CALL">foo</symbolName> = 1
}
