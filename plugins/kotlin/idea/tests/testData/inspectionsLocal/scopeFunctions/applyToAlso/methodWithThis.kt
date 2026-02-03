// WITH_STDLIB
// FIX: Convert to 'also'

val x = hashSetOf<String>().<caret>apply {
    this.add("x")
}
