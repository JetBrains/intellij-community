class Bar {
    fun test() {
        with(42) {
            topLevelExtensionProperty
        }
    }
}