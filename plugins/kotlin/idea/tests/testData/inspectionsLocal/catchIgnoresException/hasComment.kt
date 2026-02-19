// PROBLEM: none
fun test() {
    try {
        test1()
    }
    <caret>catch(x: Exception) {
        // comment
    }

}
fun test1() {}