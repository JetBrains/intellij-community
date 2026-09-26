class Bar {
    fun Main.Companion.test() {
        with(42) {
            companionExtensionProperty
        }
    }
}