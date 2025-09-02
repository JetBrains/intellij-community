class Test {
    companion object {
        val globalVar = 1
    }

    fun test() {
        <caret>Companion.globalVar
    }
}

val globalVar = 2
// KT-76525
// IGNORE_K2