// EXPECTED_DUPLICATED_HIGHLIGHTING
fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">test</symbolName>() {
    val <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">vect</symbolName> = <symbolName textAttributesKey="KOTLIN_CONSTRUCTOR">MyIterable</symbolName><<symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>>()
    <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">vect</symbolName>.<symbolName textAttributesKey="KOTLIN_FUNCTION_CALL">filter</symbolName> { <symbolName textAttributesKey="KOTLIN_CLOSURE_DEFAULT_PARAMETER">it</symbolName> != 2 }.<symbolName textAttributesKey="KOTLIN_FUNCTION_CALL">forEach</symbolName> { <symbolName textAttributesKey="KOTLIN_CLOSURE_DEFAULT_PARAMETER">it</symbolName>.<symbolName textAttributesKey="KOTLIN_FUNCTION_CALL">toString</symbolName>() }
}

class <symbolName textAttributesKey="KOTLIN_CLASS">MyIterable</symbolName><<symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>> {
    fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">filter</symbolName>(<warning textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName textAttributesKey="KOTLIN_PARAMETER">function</symbolName></warning>: (<symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>) -> <symbolName textAttributesKey="KOTLIN_CLASS">Boolean</symbolName>) = this
    fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">forEach</symbolName>(<warning textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName textAttributesKey="KOTLIN_PARAMETER">action</symbolName></warning>: (<symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>) -> <symbolName textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>) {
    }
}
