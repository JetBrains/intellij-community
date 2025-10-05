// PROBLEM: none
class Test {
    var test = "OK"
        <caret>set(value) {
            throw UnsupportedOperationException()
        }
}
