fun test() {
    class Local {
        fun foo() {
            bar<caret>
        }

        private fun bar() {}
    }
}

// INVOCATION_COUNT: 0
// EXIST: bar