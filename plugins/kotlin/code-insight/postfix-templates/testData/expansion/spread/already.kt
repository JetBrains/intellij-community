fun test() {
    call(*intArrayOf(1, 2, 3)<caret>)
}

fun call(vararg values: Int) {}