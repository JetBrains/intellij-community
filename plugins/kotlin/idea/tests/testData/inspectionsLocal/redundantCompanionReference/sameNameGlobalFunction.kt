class Test {
    companion object {
        fun globalFun() = 1
    }

    fun test() {
        <caret>Companion.globalFun()
    }
}

fun globalFun() = 2
// KT-76525
// IGNORE_K2