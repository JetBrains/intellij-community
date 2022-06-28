class Enclosing {
    companion object {
        const val x = 1
    }
}

fun main() {
    Enclosing.Companion::class.sout<caret>
}