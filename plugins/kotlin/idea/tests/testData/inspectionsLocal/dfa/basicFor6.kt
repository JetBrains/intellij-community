// PROBLEM: 'for' range is always empty
// FIX: none
fun test(x: Int, y: Int) {
    if (y >= x) return
    for(i in <caret>x..y) {
    }
}
