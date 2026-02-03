val prop: Int
    get() {
        run {
            <caret>class Foo { fun bar() = run { (fun(): Int { return field })() } }
        }
        return 0
    }

// DELETE_LINE
// OUT_OF_BLOCK: true
