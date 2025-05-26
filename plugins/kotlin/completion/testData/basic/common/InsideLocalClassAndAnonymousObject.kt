fun test() {
    class Local {
        fun foo() {
            object {
                fun foo2() {
                    bar<caret>
                }
            }
        }

        private fun bar() {}
    }
}

// INVOCATION_COUNT: 0
// EXIST: bar