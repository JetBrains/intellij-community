val prop: Int
    get() {
        <caret>
        field
        return 0
    }

// TYPE: val field = 1
// OUT_OF_BLOCK: true
