class Example(val obj: Any?) {
    companion object {
        operator fun invoke(): Example {
            return <lineMarker text="Recursive call">Example</lineMarker>()
        }
    }
}