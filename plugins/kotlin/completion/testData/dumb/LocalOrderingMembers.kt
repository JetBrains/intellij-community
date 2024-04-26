class A {
    fun prefixA() {}
    class B {
        fun prefixB() {}
        class C {
            fun prefixC() {}

            fun test() {
                val a = this.prefix<caret>
            }
        }
    }
}

// WITH_ORDER
// EXIST: prefixC, prefixB, prefixA
// NOTHING_ELSE