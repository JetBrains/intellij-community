fun f(a: Int): Int {
    <lineMarker text="Recursive call">f(a - 1) + f</lineMarker>(a + 1)
    return <lineMarker text="Recursive call">f</lineMarker>(a)
}