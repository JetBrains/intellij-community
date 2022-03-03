// WITH_STDLIB
fun test(){
    1.apply {
        listOf(1, 2, 3).apply<caret> { forEach { this.plus(it) } }
    }
}