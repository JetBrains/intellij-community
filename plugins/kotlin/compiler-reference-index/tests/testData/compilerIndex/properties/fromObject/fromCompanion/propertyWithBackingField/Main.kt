class Main {
    companion object {
        var companionVari<caret>able = 42
            get() = field
            set(value) {
                field = value
            }
    }
}
