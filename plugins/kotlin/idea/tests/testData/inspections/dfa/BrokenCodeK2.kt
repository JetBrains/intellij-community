// WITH_STDLIB
fun test() {
    try {
        // Difference with K1: error messages text is different
        <error descr="[ITERATOR_AMBIGUITY]">for(<error descr="Expecting a variable name">)</error>
    </error><error descr="Expecting an expression">}</error>
    catch (x: Throwable) {

    }
}