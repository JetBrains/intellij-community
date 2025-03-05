// PROBLEM: none
// Issue: KTIJ-31622

class StateStateRecord<T>(val myValue: T) {

    var value: T = myValue

    fun assignThree(s: StateStateRecord<T>) {
        this.value = s.value<caret>
    }
}
