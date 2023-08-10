fun test() {
    call(1, 2, java.lang.Integer.parseInt("5"))<caret>
}

@Throws(IllegalStateException::class, IllegalArgumentException::class)
fun call(vararg x: Int) {}