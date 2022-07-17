// WITH_STDLIB
fun test() {
    try {
        for(<error descr="Expecting a variable name">)</error>
    <error descr="Expecting an expression">}</error>
    catch (x: Throwable) {

    }
}