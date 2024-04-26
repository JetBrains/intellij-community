// PROBLEM: none

class B<caret>() {
    constructor(int: Int) : this()

    fun main() {
        B(2)
    }
}

// IGNORE_K1