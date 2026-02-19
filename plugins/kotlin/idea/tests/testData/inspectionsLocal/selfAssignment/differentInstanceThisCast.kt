// PROBLEM: none
// Issue: KTIJ-31622

class StateStateRecord<T>(val myValue: T) {

    var value: T = myValue

    fun assign(s: Any) {
        this.value = (s as StateStateRecord<T>).value<caret>
    }
}
