// WITH_STDLIB
fun test(s: String) {
    with<caret> (s, {
        println("1")
        println("2")
    })
}