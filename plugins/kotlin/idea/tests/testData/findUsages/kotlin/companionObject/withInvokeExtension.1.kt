fun test(bar: Bar) {
    with(bar) {
        Foos()
        Foos.Companion.invoke()
    }
}