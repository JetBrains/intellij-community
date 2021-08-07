// IGNORE_FIR

<info textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">annotation</info> class <info textAttributesKey="KOTLIN_ANNOTATION">Anno</info>
typealias <info textAttributesKey="KOTLIN_ANNOTATION">AnnoAlias</info> = <info textAttributesKey="KOTLIN_ANNOTATION">Anno</info>
<info textAttributesKey="KOTLIN_ANNOTATION">@Anno</info> fun <info textAttributesKey="KOTLIN_FUNCTION_DECLARATION">annoUsage</info>() {}

class <info textAttributesKey="KOTLIN_CLASS">Foo</info>
typealias <info textAttributesKey="KOTLIN_TYPE_ALIAS">FooAlias</info> = <info textAttributesKey="KOTLIN_CLASS">Foo</info>
fun <info textAttributesKey="KOTLIN_FUNCTION_DECLARATION">fooUsage</info>(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used" textAttributesKey="null"><info textAttributesKey="KOTLIN_PARAMETER">a</info></warning>: <info textAttributesKey="KOTLIN_TYPE_ALIAS">FooAlias</info>) {}