val prop: Int
    get() {
        <caret>val field = 1
        field
        return 0
    }

// DELETE_LINE
// OUT_OF_BLOCK: true
