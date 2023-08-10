// IS_APPLICABLE: false
fun test() {
    val i = 1
    foo<caret>(123, 456)
}

fun foo(i: Int, j: Int) {}

