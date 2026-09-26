val prop: Int
    get() {
        <caret>
        return 0
    }

// TYPE: field
// OUT_OF_BLOCK: true
