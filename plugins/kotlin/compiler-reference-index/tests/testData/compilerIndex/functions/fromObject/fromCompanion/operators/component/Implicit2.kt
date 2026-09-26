fun Main.Companion.test3() {
    with(42) {
        val (_, b) = this
    }
}