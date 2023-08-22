// WITH_STDLIB

val x = hashSetOf("abc").<caret>apply {
    forEach {
        forEach {
            println(this)
        }
    }
}
