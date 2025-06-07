package testing

<symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">data</symbolName> object <symbolName descr="null" textAttributesKey="KOTLIN_DATA_OBJECT">Obj</symbolName> {
    fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() = 42
}

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">testing</symbolName>(): <symbolName descr="null" textAttributesKey="KOTLIN_DATA_OBJECT">Obj</symbolName> {
    <symbolName descr="null" textAttributesKey="KOTLIN_DATA_OBJECT">Obj</symbolName>.<symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_CALL">foo</symbolName>()
    val <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">obj</symbolName> = <symbolName descr="null" textAttributesKey="KOTLIN_DATA_OBJECT">Obj</symbolName>
    <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">obj</symbolName>.<symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_CALL">foo</symbolName>()
    return <symbolName descr="null" textAttributesKey="KOTLIN_DATA_OBJECT">Obj</symbolName>
}
