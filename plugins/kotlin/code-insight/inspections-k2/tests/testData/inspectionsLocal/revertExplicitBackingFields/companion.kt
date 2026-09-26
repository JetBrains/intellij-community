// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class X {
    companion object {
        val items: Set<Int>
        fi<caret>eld = mutableSetOf<Int>()
    }
}

fun main() {
    if (X.items.isEmpty()) {
        println("No items left")
    }
}