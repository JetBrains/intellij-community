fun testDirectReassignment() {
    var unstable = ""
    Thread {
        println(unstable)
    }
    unstable = "hello"
}

private fun testNotCaptured() {
    Thread {
        var another = "hello"
        println(another)
    }
}

private fun testRepeated() {
    var repeat = true
    var attempts = 0
    while (repeat) {
        Thread {
            try {
                println(attempts)
                repeat = false
            } catch (e: Throwable) {
                println(e)
            }
        }
    }
}