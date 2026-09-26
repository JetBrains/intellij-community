// FIX: Convert to 'let'

class C {
    val `foo bar` = 1
}

val x = C().<caret>run {
    `foo bar`
}
