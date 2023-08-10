fun test2() {
    true.let { it.topLevelExtension("i", i3 = 42, i4 = "") }
}