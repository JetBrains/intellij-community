
// FIX: Remove unreachable code
fun f(): Int {
    <caret>throw throw throw Exception("Throw far away")
}
