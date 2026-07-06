fun test() {
    foo<<error descr="[UPPER_BOUND_VIOLATED]">Int?</error>>()
    foo<Int>()
    bar<Int?>()
    bar<Int>()
    bar<<error descr="[UPPER_BOUND_VIOLATED]">Double?</error>>()
    bar<<error descr="[UPPER_BOUND_VIOLATED]">Double</error>>()
    1.<error descr="[INAPPLICABLE_CANDIDATE]">buzz</error><Double>()
}

fun <T : Any> foo() {}
fun <T : Int?> bar() {}
fun <T : Int> Int.buzz() : Unit {}
