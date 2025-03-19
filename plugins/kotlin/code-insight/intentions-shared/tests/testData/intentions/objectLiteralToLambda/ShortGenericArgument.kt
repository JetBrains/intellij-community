// AFTER-WARNING: Variable 'myUser' is never used
fun use() {
    val myUser = <caret>object : User<String> {
        override fun call(arg: String) {}
    }
}