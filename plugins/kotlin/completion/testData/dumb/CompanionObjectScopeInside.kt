class A {
    companion object {
        val prefixTest = 5

        fun a() {
            println(prefix<caret>)
        }
    }
}

// EXIST: prefixTest
// NOTHING_ELSE