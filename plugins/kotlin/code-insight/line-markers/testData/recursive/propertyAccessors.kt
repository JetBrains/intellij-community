class A {
    var mutable = 0
        set(value) {
            <lineMarker descr="Recursive call">mutable</lineMarker><lineMarker text="Recursive call">++</lineMarker>
            <lineMarker descr="Recursive call">mutable</lineMarker><lineMarker text="Recursive call">+=</lineMarker>1
            <lineMarker descr="Recursive call">mutable</lineMarker> <lineMarker text="Recursive call">=</lineMarker> value
        }

    var immutable = 0
        get() {
            println("$<lineMarker text="Recursive call">immutable</lineMarker>")
            <lineMarker descr="Recursive call">immutable</lineMarker><lineMarker text="Recursive call">++</lineMarker>
            <lineMarker descr="Recursive call">immutable</lineMarker> <lineMarker text="Recursive call">+=</lineMarker> 1
            return <lineMarker text="Recursive call">immutable</lineMarker>
        }

    var simpleGetter = 0
        get() = <lineMarker text="Recursive call">simpleGetter</lineMarker>

    var field = 0
        get() {
            return if (field != 0) field else -1
        }
        set(value) {
            if (value >= 0) field = value
        }
}