val prop: Int
    get() {
        <caret>field
        return 0
    }

// DELETE_LINE
// OUT_OF_BLOCK: true
