// HIGHLIGHT: INFORMATION
// PROBLEM: Use expression body instead of return

fun simple(): Int {
    ret<caret>urn 1 *
           (2 + 3)
}