// FIR_IDENTICAL

package test2

<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">enum</symbolName> class <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName> { <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">A</symbolName>, <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">B</symbolName>, <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">С</symbolName> }

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">main</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'args' is never used" tooltip="[UNUSED_PARAMETER] Parameter 'args' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">args</symbolName></warning>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Array</symbolName><<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>>) {
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">en2</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Any</symbolName>? = <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName>.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">A</symbolName>
    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">en2</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName>) {
        when (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="Smart cast to test2.En" tooltip="Smart cast to test2.En" textAttributesKey="KOTLIN_SMART_CAST_VALUE">en2</symbolName></symbolName>) {
            <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName>.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">A</symbolName> -> {}
            <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName>.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">B</symbolName> -> {}
            <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM">En</symbolName>.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_ENUM_ENTRY">С</symbolName> -> {}
        }
    }
}
