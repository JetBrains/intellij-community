class A {
    var mutable = 0
        set(value) {
            mutable<lineMarker text="Recursive call">++</lineMarker>
            mutable<lineMarker text="Recursive call">+=</lineMarker>1
            mutable <lineMarker text="Recursive call">=</lineMarker> value
        }

    var immutable = 0
        get() {
            println("$<lineMarker text="Recursive call">immutable</lineMarker>")
            immutable<lineMarker text="Recursive call">++</lineMarker>
            immutable <lineMarker text="Recursive call">+=</lineMarker> 1
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