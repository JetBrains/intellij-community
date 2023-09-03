fun <U, V> cast(param: U): V? = param as? V

// U - inferrable from 'param' => (U)
// V - non inferrable (return value type is not taken into account)

fun test() {
    val res: Long? = cast(5).<caret>
}

// IGNORE_K2
// ELEMENT: minus
// TAIL_TEXT: "(other: Int)"