class Main {
    companion object {
        @JvmStatic
        var Int.companionExt<caret>ensionProperty
            get() = 42
            set(value) {}
    }
}
