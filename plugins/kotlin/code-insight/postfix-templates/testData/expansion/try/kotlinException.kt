fun test() {
    call()<caret>
}

@Throws(IllegalStateException::class, IllegalArgumentException::class)
fun call() {}