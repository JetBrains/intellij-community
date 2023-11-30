// IGNORE_K2

<symbolName textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">annotation</symbolName> class <symbolName textAttributesKey="KOTLIN_ANNOTATION">Anno</symbolName>
typealias <symbolName textAttributesKey="KOTLIN_ANNOTATION">AnnoAlias</symbolName> = <symbolName textAttributesKey="KOTLIN_ANNOTATION">Anno</symbolName>
<symbolName textAttributesKey="KOTLIN_ANNOTATION">@Anno</symbolName> fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">annoUsage</symbolName>() {}

class <symbolName textAttributesKey="KOTLIN_CLASS">Foo</symbolName>
typealias <symbolName textAttributesKey="KOTLIN_TYPE_ALIAS">FooAlias</symbolName> = <symbolName textAttributesKey="KOTLIN_CLASS">Foo</symbolName>
fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">fooUsage</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used" textAttributesKey="null"><symbolName textAttributesKey="KOTLIN_PARAMETER">a</symbolName></warning>: <symbolName textAttributesKey="KOTLIN_TYPE_ALIAS">FooAlias</symbolName>) {}