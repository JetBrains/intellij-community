// WITH_STDLIB
fun test(t: Test) {
    val result = t.returnT() as? String
    if (result == null) {}
}

interface Test {
    fun <T:Any> returnT() : T
}
