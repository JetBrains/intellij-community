// WITH_STDLIB
// FIX: Convert to 'also'

val x = "".also {
    "".<caret>apply {
        this.length + it.length
    }
}
