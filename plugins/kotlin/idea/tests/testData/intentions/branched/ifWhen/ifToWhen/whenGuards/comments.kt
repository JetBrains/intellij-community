// COMPILER_ARGUMENTS: -Xwhen-guards
// Issue about misplaced comments: KTIJ-32066

fun test(a: Any, b: Boolean) {
    /* c1 */
    // c2
    if<caret> (a is Int /* c3 */ && a > 5) { // c4
    } else if (/* c5 */ a is String && /* c6 */ a.isNotEmpty() /* c7 */) { //c8
    } else {
        // c9
    }
}
