fun foo(start: Int = 999, paramTest: Int = 12) {

}

fun test() {
    foo(param<caret> start = 100)
}
