// PROBLEM: none
// WITH_STDLIB


val a = randomValue().let<caret> { r ->
    List(10, fun(it: Int): Int {
        return r
    })
}

fun randomValue(): Int = 42