// FIX: Use property access syntax

class K: J() {

    fun setButReturnUnit(value: Int) {
        <caret>setX(value)
    }
}