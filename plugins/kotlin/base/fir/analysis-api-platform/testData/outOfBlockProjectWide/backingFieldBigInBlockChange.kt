val prop: Int
    get() {
        <caret>run { class Foo { fun bar() = run { (fun() { return Unit })() } } }
        return 0
    }

// DELETE_LINE
// OUT_OF_BLOCK: false
