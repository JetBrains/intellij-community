// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING
interface <symbolName textAttributesKey="KOTLIN_TRAIT">Zoo</symbolName><<symbolName textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>> {
    <error descr="[WRONG_MODIFIER_TARGET] Modifier 'inner' is not applicable to 'enum class'" textAttributesKey="ERRORS_ATTRIBUTES"><symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">inner</symbolName></error> <symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">enum</symbolName> class <symbolName textAttributesKey="KOTLIN_ENUM">Var</symbolName> : <symbolName textAttributesKey="KOTLIN_TRAIT">Zoo</symbolName><<error descr="[INACCESSIBLE_OUTER_CLASS_EXPRESSION] Expression is inaccessible from a nested class 'Var'" textAttributesKey="ERRORS_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName></error>>
}

object <symbolName textAttributesKey="KOTLIN_OBJECT">Outer</symbolName> {
    fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">bar</symbolName>() = <symbolName textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>
    class <symbolName textAttributesKey="KOTLIN_CLASS">Inner</symbolName>  {
        fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() = this<symbolName textAttributesKey="KOTLIN_LABEL"><error descr="[INACCESSIBLE_OUTER_CLASS_EXPRESSION] Expression is inaccessible from a nested class 'Inner'" textAttributesKey="ERRORS_ATTRIBUTES">@Outer</error></symbolName>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">bar</error>()
    }
}