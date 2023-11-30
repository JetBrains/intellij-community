// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING
class <symbolName descr="null">X</symbolName><<symbolName descr="null">T</symbolName>>{}
fun <<symbolName descr="null">T</symbolName>> <symbolName descr="null">foo</symbolName>() : <symbolName descr="null">X</symbolName><<symbolName descr="null">T</symbolName>> {<error descr="[NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY] A 'return' expression required in a function with a block body ('{...}')">}</error>
val <symbolName descr="null">x</symbolName> <symbolName descr="null">by</symbolName> <error descr="[DELEGATE_SPECIAL_FUNCTION_MISSING] Type 'X<TypeVariable(T)>' has no method 'getValue(Nothing?, KProperty<*>)' and thus it cannot serve as a delegate" textAttributesKey="ERRORS_ATTRIBUTES">foo<<error descr="Type expected">></error>()</error>