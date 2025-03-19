// FIR_IDENTICAL

interface <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">A</symbolName> {
    fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>()
}

interface <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">B</symbolName> : <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">A</symbolName> {
    fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">bar</symbolName>()
}

interface <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">C</symbolName>

interface <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">D</symbolName> : <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">A</symbolName>

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">test</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">A</symbolName>?) {
    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> != null && <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">B</symbolName>?) {
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to B" tooltip="Smart cast to B" textAttributesKey="KOTLIN_SMART_CAST_VALUE">a</symbolName></symbolName>.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_CALL">bar</symbolName>()
    }

    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">B</symbolName> && <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">C</symbolName>) {
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to B" tooltip="Smart cast to B" textAttributesKey="KOTLIN_SMART_CAST_VALUE">a</symbolName></symbolName>.<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_CALL">foo</symbolName>()
    }

    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">B</symbolName>? && <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">C</symbolName>?) {
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to B?" tooltip="Smart cast to B?" textAttributesKey="KOTLIN_SMART_CAST_VALUE">a</symbolName></symbolName><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_SAFE_ACCESS">?.</symbolName><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_CALL">bar</symbolName>()
    }

    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_SAFE_ACCESS">?.</symbolName><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_CALL">foo</symbolName>()
    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">B</symbolName>? && <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">C</symbolName>?) {
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_SAFE_ACCESS">?.</symbolName><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_CALL">foo</symbolName>()
    }

    if (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">B</symbolName> && <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName> is <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TRAIT">D</symbolName>) {
        //when it's resolved, the message should be 'Smart cast to A'
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="Smart cast to B" tooltip="Smart cast to B" textAttributesKey="KOTLIN_SMART_CAST_VALUE">a</symbolName></symbolName>.<error descr="[FUNCTION_CALL_EXPECTED] Function invocation 'foo()' expected" tooltip="[FUNCTION_CALL_EXPECTED] Function invocation 'foo()' expected" textAttributesKey="ERRORS_ATTRIBUTES">foo</error>
    }
}
