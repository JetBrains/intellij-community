// "Replace with 'C'" "true"
@Deprecated("", replaceWith = ReplaceWith("C"))
private typealias A = C

private class C {
    companion object {
        val x = 1
    }
}

fun f() {
    val x = <caret>A.x
}
