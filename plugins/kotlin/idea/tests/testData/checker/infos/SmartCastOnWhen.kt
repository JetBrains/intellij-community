// FIR_IDENTICAL

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">baz</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>?): <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName> {
    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName> == null) return 0
    return when(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to kotlin.String" tooltip="Smart cast to kotlin.String" textAttributesKey="KOTLIN_SMART_CAST_VALUE">s</symbolName></symbolName>) {
        "abc" -> <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to kotlin.String (for null call)" tooltip="Smart cast to kotlin.String (for null call)" textAttributesKey="KOTLIN_SMART_CAST_VALUE"><symbolName textAttributesKey="KOTLIN_SMART_CAST_VALUE">s</symbolName></symbolName></symbolName>
        else -> "xyz"
    }.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">length</symbolName>
}
