// WITH_STDLIB

val x = hashSetOf("abc").<caret>apply {
    forEach {
        println(this)
    }
}
