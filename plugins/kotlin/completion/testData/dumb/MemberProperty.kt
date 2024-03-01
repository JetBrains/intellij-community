class Test {
    val someProperty = 5
    fun someFunction() {

    }
}

val test = 5.some<caret>

// EXIST: someProperty, someFunction
// NOTHING_ELSE