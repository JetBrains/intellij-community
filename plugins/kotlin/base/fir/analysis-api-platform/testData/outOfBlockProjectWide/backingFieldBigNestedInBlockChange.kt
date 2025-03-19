val prop: Int
    get() {
        run {
            <caret>class Foo { fun bar() = run { (fun() { return Unit })() } }
        }
        return 0
    }

// DELETE_LINE
// OUT_OF_BLOCK: false
