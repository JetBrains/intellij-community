// PROBLEM: none
package my.simple.name

class Child {
    fun f() {
        Companion<caret>.value
    }

    companion object {
        val value = 1
    }
}

// IGNORE_K2