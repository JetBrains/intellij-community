val prop: Int
    get() {
        run {
            run {
                class Foo {
                    fun baz() {
                        <caret>
                    }
                }
            }
        }

        return 0
    }

// TYPE: some()
// OUT_OF_BLOCK: false
