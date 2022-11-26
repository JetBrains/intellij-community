operator fun Any.get(a: Int) {
    <lineMarker text="Recursive call">this</lineMarker>[a - 1]
}

class Foo {
    override fun equals(other: Any?): Boolean {
        this <lineMarker text="Recursive call">==</lineMarker> other
        return true
    }

    operator fun inc(): Foo {
        // TODO Fix Analysis API resolution for explicit 'this' receivers
        this++
        ++this
        return this
    }

    operator fun component1(): Int {
        // TODO: should be recursion marker too
        val (<lineMarker text="Recursive call">a</lineMarker>) = this
        return 1
    }

    operator fun unaryPlus() {
        <lineMarker text="Recursive call">+</lineMarker>this
    }

    operator fun unaryMinus() {
        <lineMarker text="Recursive call">-</lineMarker>this
    }

    operator fun plus(a: Int) {
        this <lineMarker text="Recursive call">+</lineMarker> 1
        this += 1
    }

    operator fun invoke() {
        val a = Foo()
        a()
        a.invoke()

        this.<lineMarker text="Recursive call">invoke</lineMarker>()
        <lineMarker text="Recursive call">this</lineMarker>()
    }
}

class Bar

operator fun Bar.invoke() {
    <lineMarker text="Recursive call">this</lineMarker>()
    <lineMarker text="Recursive call">invoke</lineMarker>()
}