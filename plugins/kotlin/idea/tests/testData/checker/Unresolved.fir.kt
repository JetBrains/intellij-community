package unresolved

class Pair<A, B>(a: A, b: B)

fun testGenericArgumentsCount() {
    val p1: <error descr="[WRONG_NUMBER_OF_TYPE_ARGUMENTS]">Pair<Int></error> = Pair(2, 2)
    val p2: <error descr="[WRONG_NUMBER_OF_TYPE_ARGUMENTS]">Pair</error> = Pair(2, 2)
}

fun testUnresolved() {
    if (<error descr="[UNRESOLVED_REFERENCE]">a</error> is String) {
        val s = <error descr="[UNRESOLVED_REFERENCE]">a</error>
    }
    <error descr="[UNRESOLVED_REFERENCE]">foo</error>(<error descr="[UNRESOLVED_REFERENCE]">a</error>)
    val s = "s"
    <error descr="[UNRESOLVED_REFERENCE]">foo</error>(s)
    foo1(<error descr="[UNRESOLVED_REFERENCE]">i</error>)
    s.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()

    when(<error descr="[UNRESOLVED_REFERENCE]">a</error>) {
        is Int -> <error descr="[UNRESOLVED_REFERENCE]">a</error>
        is String -> <error descr="[UNRESOLVED_REFERENCE]">a</error>
        else -> <error descr="[UNRESOLVED_REFERENCE]">a</error>
    }

    for (j in <error descr="[ITERATOR_MISSING]"><error descr="[UNRESOLVED_REFERENCE]">collection</error></error>) {
       var i: Int = j
       i += 1
       foo1(j)
    }
}

fun foo1(i: Int) {}
