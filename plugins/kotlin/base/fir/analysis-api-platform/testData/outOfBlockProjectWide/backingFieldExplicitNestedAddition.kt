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

// TYPE: field
// OUT_OF_BLOCK: true
