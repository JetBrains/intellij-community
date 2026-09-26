// FIX: Convert to 'run'

class C {
    val `foo bar` = 1

    val x = "abc".<caret>let {
        it.length + `foo bar`
    }
}
