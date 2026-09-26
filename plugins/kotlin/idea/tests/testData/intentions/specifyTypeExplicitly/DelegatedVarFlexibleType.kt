class P<T : Any>(
    private val k: Key<T>,
    private var v: T,
) {
    operator fun getValue(r: Any?, p: Any?): T = v

    operator fun setValue(r: Any?, p: Any?, x: T) {
        v = x
    }
}

var Any.flag<caret> by P(Key.create(), true)