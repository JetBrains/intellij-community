val prop: Int
    get() {
        <caret>run { class Foo { fun bar() = run { (fun() { return Unit })() } } }
        field
        return 0
    }

// DELETE_LINE

// this should be in-block modification, but the platform decides to recreate the entire body
// OUT_OF_BLOCK: true
