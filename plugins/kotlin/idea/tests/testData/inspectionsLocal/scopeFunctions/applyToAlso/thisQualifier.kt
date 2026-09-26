// WITH_STDLIB
// FIX: Convert to 'also'

class C {
    val x = hashSetOf<C>().<caret>apply {
        add(this@C)
    }
}
