fun <U, V> cast(param: U): V? = param as? V
fun test() {
    val res: Long? = cast(5).<caret>
}

// ELEMENT: minus
// TAIL_TEXT: "(other: Int)"