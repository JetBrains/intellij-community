// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun test() {
    class Test {
        operator fun unaryPlus(): Test = Test()
        operator fun plus(a: Test): Test = Test()
        operator fun unaryMinus(): Test = Test()
    }
    val test = Test()
    doSomething((-((test + test).unaryPl<caret>us())).toString())
}
