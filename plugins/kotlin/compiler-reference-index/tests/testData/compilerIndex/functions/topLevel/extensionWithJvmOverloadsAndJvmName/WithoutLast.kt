fun test() {
    true.run {
        topLevelExtension("i", true, 42)
    }
}