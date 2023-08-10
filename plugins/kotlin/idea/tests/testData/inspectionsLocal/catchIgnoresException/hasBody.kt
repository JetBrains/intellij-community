// PROBLEM: none
fun test() {
    try {
        test1()
    }
    <caret>catch(x: Exception) {
        x.printStackTrace()
    }

}
fun test1() {}