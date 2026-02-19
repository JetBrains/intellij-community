fun test() {
    class Local {
        fun foo() {
            bar<caret>
        }

        fun bar() {}
    }
}

// INVOCATION_COUNT: 0
// EXIST: bar