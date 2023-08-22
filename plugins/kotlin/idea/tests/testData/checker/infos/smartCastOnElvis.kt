// FIR_IDENTICAL

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>) = <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName>.<symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">length</symbolName>

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">baz</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>?, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">r</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>?): <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName> {
    return <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">foo</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">r</symbolName> ?: when {
        <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName> != null -> <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to kotlin.String (for null call)" textAttributesKey="KOTLIN_SMART_CAST_VALUE"><symbolName textAttributesKey="KOTLIN_SMART_CAST_VALUE">s</symbolName></symbolName></symbolName>
        else -> ""
    })
}

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">bar</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>?, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">r</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>?): <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName> {
    return (<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">r</symbolName> ?: when {
        <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName> != null -> <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to kotlin.String (for null call)" textAttributesKey="KOTLIN_SMART_CAST_VALUE"><symbolName textAttributesKey="KOTLIN_SMART_CAST_VALUE">s</symbolName></symbolName></symbolName>
        else -> ""
    }).<symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">length</symbolName>
}
