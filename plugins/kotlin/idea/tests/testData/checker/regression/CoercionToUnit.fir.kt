fun foo(u : Unit) : Int = 1

fun test() : Int {
    foo(<error descr="[ARGUMENT_TYPE_MISMATCH]">1</error>)
    val a : () -> Unit = {
        foo(<error descr="[ARGUMENT_TYPE_MISMATCH]">1</error>)
    }
    return 1
}
