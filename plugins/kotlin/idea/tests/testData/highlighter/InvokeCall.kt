// EXPECTED_DUPLICATED_HIGHLIGHTING
fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">test</symbolName>() {
    <symbolName textAttributesKey="KOTLIN_CONSTRUCTOR">Test</symbolName>("text", "text")() // BUG
}

class <symbolName textAttributesKey="KOTLIN_CLASS">Test</symbolName>(val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">x</symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">String</symbolName>, val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">y</symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">String</symbolName>) {
    <symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">operator</symbolName> fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">invoke</symbolName>() {
    }
}
