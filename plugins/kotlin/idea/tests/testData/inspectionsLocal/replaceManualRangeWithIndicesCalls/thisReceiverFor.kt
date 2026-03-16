// WITH_STDLIB
// FIX: Replace with loop over elements
fun Array<String>.test() {
    for (index in <caret>0..this.size - 1) {
        val out = this[index]
    }
}
