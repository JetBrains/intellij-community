// CHOOSE_NULLABLE_TYPE_IF_EXISTS
class P<T : Any>(
    private val k: Key<T>,
    private var v: T,
) {
    operator fun getValue(r: Any?, p: Any?): T = v
}

val Any.flag<caret> by P(Key.create(), true)
