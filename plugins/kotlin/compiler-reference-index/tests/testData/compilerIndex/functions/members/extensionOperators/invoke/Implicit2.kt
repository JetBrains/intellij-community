fun test3() {
    Main().run {
        with(42) {
            invoke("")
        }
    }
}