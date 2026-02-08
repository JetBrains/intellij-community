// WITH_STDLIB
data class A(val b: B)
data class B(val c: C)
data class C(val value: String)

fun test(obj: A) {
    i<caret>f (obj.b.c.value == "a") {
        println("a")
    } else if (obj.b.c.value == "b") {
        println("b")
    } else if (obj.b.c.value == "c") {
        println("c")
    }
}
