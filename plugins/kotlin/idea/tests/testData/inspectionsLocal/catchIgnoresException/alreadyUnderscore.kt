// PROBLEM: none
fun test() {
    try {
        test1()
    }
    <caret>catch(_: Exception) {
    }

}
fun test1() {}