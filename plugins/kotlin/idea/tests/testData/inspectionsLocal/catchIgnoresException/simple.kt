// PROBLEM: Empty catch block
// FIX: Rename 'ex' to '_'
fun test() {
    try {
        test1()
    }
    <caret>catch(ex: Exception) {
    }

}
fun test1() {}