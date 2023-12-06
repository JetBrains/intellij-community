// FIR_IDENTICAL

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">baz</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>?): <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName> {
    return if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName> == null) {
        ""
    }
    else {
        val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">u</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>? = null
        if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">u</symbolName> == null) return 0
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="Smart cast to kotlin.String" tooltip="Smart cast to kotlin.String" textAttributesKey="KOTLIN_SMART_CAST_VALUE">u</symbolName></symbolName>
    }.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">length</symbolName>
}
