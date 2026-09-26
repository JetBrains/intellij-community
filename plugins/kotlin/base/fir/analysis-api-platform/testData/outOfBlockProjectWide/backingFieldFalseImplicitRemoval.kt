val prop: Int
    get() {
        field
        <caret>
        return 0
    }

// TYPE: val field = 1
// OUT_OF_BLOCK: true
