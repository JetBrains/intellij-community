// LANGUAGE_VERSION: 1.9
val range = 0/*<# ≤ #>*/../*<# ≤ #>*/10
val rangeUntil = 0/*<# ≤ #>*/..<10

val errorRange = 4downTo 0

class Foo : Comparable<Foo> {
    override fun compareTo(other: Foo): Int = TODO("Not yet implemented")
}

fun foo() {
    // rangeTo and rangeUntil are not an infix functions and shouldn't have hints
    for (index in 0 rangeTo 100) {}
    for (index in 0 rangeUntil 100) {}

    for (index in 0.rangeTo(100)) {}
    for (index in 'a'.rangeTo('z')) {}
    for (index in 0L.rangeTo(100L)) {}
    for (index in Foo().rangeTo(Foo())) {}
    for (index in 0.0.rangeTo(100.0)) {}
    for (index in 0f.rangeTo(100f)) {}

    for (index in 0/*<# ≤ #>*/ .. /*<# ≤ #>*/100) {}
    for (index in 'a'/*<# ≤ #>*/ .. /*<# ≤ #>*/'z') {}
    for (index in 0L/*<# ≤ #>*/ .. /*<# ≤ #>*/100L) {}
    for (index in Foo()/*<# ≤ #>*/ .. /*<# ≤ #>*/Foo()) {}
    for (index in 0.0/*<# ≤ #>*/ .. /*<# ≤ #>*/100.0) {}
    for (index in 0f/*<# ≤ #>*/ .. /*<# ≤ #>*/100f) {}

    for (index in 0/*<# ≤ #>*/ ..< 100) {}
    for (index in 'a'/*<# ≤ #>*/ ..< 'z') {}
    for (index in 0.0/*<# ≤ #>*/ ..< 100.0) {}
    for (index in 0f/*<# ≤ #>*/ ..< 100f) {}

    for (index in 0.rangeUntil(100)) {}
    for (index in 'a'.rangeUntil('z')) {}
    for (index in 0L.rangeUntil(100L)) {}
    for (index in Foo().rangeUntil(Foo())) {}
    for (index in 0.0.rangeUntil(100.0)) {}
    for (index in 0f.rangeUntil(100f)) {}

    for (index in 0/*<# ≤ #>*/ until /*<# < #>*/100) {}
    for (index in 'a'/*<# ≤ #>*/ until /*<# < #>*/'z') {}
    for (index in 0L/*<# ≤ #>*/ until /*<# < #>*/100L) {}

    for (index in 100/*<# ≥ #>*/ downTo /*<# ≥ #>*/0) {}
    for (index in 'z'/*<# ≥ #>*/ downTo /*<# ≥ #>*/'a') {}
    for (index in 100L/*<# ≥ #>*/ downTo /*<# ≥ #>*/0L) {}

    for (i in 0 until 0/*<# ≤ #>*/../*<# ≤ #>*/5 ) {}
    for (i in 1/*<# ≤ #>*/ until /*<# < #>*/10 step 2) {}
    run {
        val left: Short = 0
        val right: Short = 10

        for (i in left.rangeTo(right)) {}
        for (i in left/*<# ≤ #>*/ .. /*<# ≤ #>*/right) {}
        for (i in left/*<# ≤ #>*/ ..< right) {}
        for (i in left/*<# ≤ #>*/ until /*<# < #>*/right) {}
        for (index in right/*<# ≥ #>*/ downTo /*<# ≥ #>*/left) {}
        for (index in left.rangeUntil(right)) {}
    }

    for (index in someVeryVeryLongLongLongLongFunctionName(0)/*<# ≤ #>*/ .. /*<# ≤ #>*/someVeryVeryLongLongLongLongFunctionName(100)) {}

    val list = emptyList<String>()

    val string = "abc" + list..map {

    }
}

private infix fun Int.until(intRange: IntRange): IntRange = TODO()

private fun someVeryVeryLongLongLongLongFunctionName(x: Int): Int = x

private fun check(x: Int, y: Int) {
    val b = x in 8/*<# ≤ #>*/../*<# ≤ #>*/9
    if (x in 7/*<# ≤ #>*/../*<# ≤ #>*/9 && y in 5/*<# ≤ #>*/../*<# ≤ #>*/9) {
    }
}

fun test(x: Int, list: List<Int>, map: Map<String, String>) {
    10 in 0/*<# ≤ #>*/../*<# ≤ #>*/list.lastIndex
    10 in list.lastIndex/*<# ≤ #>*/../*<# ≤ #>*/100
    10 in 0/*<# ≤ #>*/ .. /*<# ≤ #>*/map["foo"]
    10 in map["foo"]/*<# ≤ #>*/ .. /*<# ≤ #>*/100
}
