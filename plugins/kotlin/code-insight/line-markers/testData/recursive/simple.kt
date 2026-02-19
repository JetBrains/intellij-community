fun f(a: Int) {
    if (a > 0) {
        <lineMarker text="Recursive call">f</lineMarker>(a - 1)
    }
}