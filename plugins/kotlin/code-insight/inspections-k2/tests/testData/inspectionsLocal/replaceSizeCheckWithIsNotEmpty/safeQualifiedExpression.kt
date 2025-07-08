// WITH_STDLIB
// PROBLEM: none

fun m(s: List<String>?) {
    if (s?.size <caret>!= 0) {
        // do smth
    }
}