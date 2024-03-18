// EXPECTED_DUPLICATED_HIGHLIGHTING
/**
 * @param <symbolName descr="null" textAttributesKey="KDOC_LINK">x</symbolName> foo and <symbolName descr="null" textAttributesKey="KDOC_LINK">[baz]</symbolName>
 * @param <symbolName descr="null" textAttributesKey="KDOC_LINK">y</symbolName> bar
 * @return notALink here
 */
fun <symbolName descr="null">f</symbolName>(<symbolName descr="null">x</symbolName>: <symbolName descr="null">Int</symbolName>, <symbolName descr="null">y</symbolName>: <symbolName descr="null">Int</symbolName>): <symbolName descr="null">Int</symbolName> {
return <symbolName descr="null">x</symbolName> + <symbolName descr="null">y</symbolName>
}

fun <symbolName descr="null">baz</symbolName>() {}