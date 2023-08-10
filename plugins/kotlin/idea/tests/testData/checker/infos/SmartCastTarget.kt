// FIR_IDENTICAL

// See KT-15901

<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">enum</symbolName> class <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName> { <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">A</symbolName> }

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(): <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Any</symbolName> {
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">en2</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Any</symbolName>? = <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName>.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">A</symbolName>
    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">en2</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName>) {
        // Here we had smart casts to En / Any
        // should be always En
        val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">a</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Any</symbolName> = <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="Smart cast to En" tooltip="Smart cast to En" textAttributesKey="KOTLIN_SMART_CAST_VALUE">en2</symbolName></symbolName>
        return <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">a</symbolName>
    }
    return ""
}
