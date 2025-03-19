// IGNORE_K2
@DslMarker
annotation class Marker

@Marker
class A {
    val fooVal = ""

    fun section(block: B.() -> Unit) {}
}

@Marker
class B {
    fun fooFun() {}
}

fun x() {
    A().apply {
        section {
            foo<caret>
        }
    }
}

// EXIST: fooFun
// ABSENT: fooVal