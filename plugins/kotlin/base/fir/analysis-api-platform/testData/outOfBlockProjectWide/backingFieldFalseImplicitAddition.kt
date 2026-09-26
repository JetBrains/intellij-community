val prop: Int
    get() {
        field
        <caret>val field = 1
        return 0
    }

// DELETE_LINE
// OUT_OF_BLOCK: true
