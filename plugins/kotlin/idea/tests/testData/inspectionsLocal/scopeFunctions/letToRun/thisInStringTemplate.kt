// WITH_STDLIB
// FIX: Convert to 'run'

class C {
    val c = "abc".<caret>let {
        println("$it")
    }
}
