// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING

<symbolName descr="null">data</symbolName> class <symbolName descr="null">Box</symbolName>(val <symbolName descr="null">v</symbolName>: <symbolName descr="null">Int</symbolName>)
fun <symbolName descr="null">consume</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'x' is never used"><symbolName descr="null">x</symbolName></warning>: <symbolName descr="null">Int</symbolName>) {}

fun <symbolName descr="null">some</symbolName>() {
    val (<symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE">s</symbolName>) = <symbolName descr="null">Box</symbolName>(0)
    var (<symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName>) = <symbolName descr="null">Box</symbolName>(1)

    <symbolName descr="null">consume</symbolName>(<symbolName descr="null">s</symbolName>)
    <symbolName descr="null">consume</symbolName>(<symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName>)

    <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName> = <symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName> * 2 + 2
    <symbolName descr="null">consume</symbolName>(<symbolName textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName>)
}
