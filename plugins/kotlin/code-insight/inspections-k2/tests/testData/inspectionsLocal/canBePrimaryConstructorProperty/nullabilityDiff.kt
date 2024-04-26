// PROBLEM: none
class SomeClass (stringValue: String) {
    var <caret>stringValue: String? = stringValue

    fun someFun() {
        stringValue = null
    }
}