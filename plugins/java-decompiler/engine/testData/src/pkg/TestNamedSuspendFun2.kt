
suspend fun foo2(): Int {
    while (true) {
        try {
            val x = bar()
            if (x == 0) break
        } finally {
            bar()
        }
    }
    return 1
}

suspend fun bar(): Int = 0
