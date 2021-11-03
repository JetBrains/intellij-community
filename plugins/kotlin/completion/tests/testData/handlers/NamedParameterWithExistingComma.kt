fun foo(first: Int = 999, second: Int = 12, third: Int = 99) {

}

fun test() {
    foo(first = 2, thir<caret>, second = 100)
}
