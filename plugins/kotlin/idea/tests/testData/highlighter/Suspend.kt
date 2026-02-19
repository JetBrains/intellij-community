// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING

val <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_PROPERTY">fnType</symbolName> : <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">suspend</symbolName> () -> <symbolName descr="null" textAttributesKey="KOTLIN_OBJECT">Unit</symbolName> = {}

val <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_PROPERTY">fnFnType</symbolName>: () -> <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">suspend</symbolName> () -> <symbolName descr="null" textAttributesKey="KOTLIN_OBJECT">Unit</symbolName> = {  -> {}}

<symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">suspend</symbolName> fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">inSuspend</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">fn</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">suspend</symbolName> () -> <symbolName descr="null" textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>) {
    val <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">res</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">suspend</symbolName> (<symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>) -> <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName> = { <symbolName descr="Automatically declared based on the expected type" textAttributesKey="KOTLIN_CLOSURE_DEFAULT_PARAMETER">it</symbolName> + 1 };
    <symbolName descr="null" textAttributesKey="KOTLIN_CONSTRUCTOR">T2</symbolName>().<symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_CALL">nonSuspend</symbolName>()
    .<symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">suspend1</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">fn</symbolName>)
    .<symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">suspend1</symbolName> {  }
        .<symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">suspend1</symbolName> { <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">res</symbolName></symbolName>(5) }
    <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">res</symbolName></symbolName>(5)
    <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_PROPERTY"><symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">fnType</symbolName></symbolName>()
    <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_PROPERTY"><symbolName descr="null" textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">fnFnType</symbolName></symbolName>().<symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">invoke</symbolName>()
}
class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">T2</symbolName> {
    <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">suspend</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">inline</symbolName> fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">suspend1</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">block</symbolName>: <warning descr="[REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE] Redundant 'suspend' modifier: lambda parameters of suspend function type uses existing continuation." textAttributesKey="WARNING_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">suspend</symbolName></warning> () -> <symbolName descr="null" textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>): <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">T2</symbolName> {
        <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="null" textAttributesKey="KOTLIN_SUSPEND_FUNCTION_CALL">block</symbolName></symbolName>()
        return this
    }
    fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">nonSuspend</symbolName>() = this
}
