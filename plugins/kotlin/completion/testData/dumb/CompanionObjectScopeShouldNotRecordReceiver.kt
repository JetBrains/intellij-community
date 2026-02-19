class A {
    companion object {
        val prefixTest = 5

        fun test() {
            val a = this.prefix<caret>
        }
    }
}

// ABSENT: prefixTest
// NOTHING_ELSE