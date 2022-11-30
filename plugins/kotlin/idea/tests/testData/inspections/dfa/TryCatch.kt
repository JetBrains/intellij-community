// WITH_STDLIB
fun castToJvm() {
    // KTIJ-23656
    try {
        println()
    }
    catch (e: Exception) {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        e as java.lang.Throwable
    }
}
fun divisionByZero(x: Int) {
    val result = try { 100 / x } catch(ex: ArithmeticException) { 200 }
    if (result == 200) {
        if (<warning descr="Condition 'x == 0' is always true"><weak_warning descr="Value of 'x' is always zero">x</weak_warning> == 0</warning>) {}
    }
}
fun tryAsArgument() {
    X.x(try {X.get()} catch(e: Exception) {null}).trim()
}
class X {
    companion object {
        fun x(x: String?):String = x?:"abc"
        fun get(): String = "xyz"
    }
}