// PROBLEM: none
// Issue: KTIJ-31622

class StateStateRecord<T>(val myValue: T) {

    var value: T = myValue

    fun assignTwo(t: T) {
        this.value = StateStateRecord<T>(t).value<caret>
    }
}
