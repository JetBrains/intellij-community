// PROBLEM: none
fun foo(flag: Boolean) {
    var x: Double<caret>
    if (flag) {
        x = 3.14
    }
    x = 2.71
}