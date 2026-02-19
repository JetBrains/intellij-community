// PROBLEM: none

class B() {
    constructor<caret>(int: Int) : this()

    fun main() {
        B(2)
    }
}