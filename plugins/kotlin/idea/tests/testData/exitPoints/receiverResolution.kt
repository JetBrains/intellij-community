// WITH_STDLIB
fun f() {
    val outer = <info descr="null">buildList</info> {
        <info descr="null">add</info>(1)
        buildString {
            this.append("")
            this@buildList.<info descr="null">~add</info>(2)   // labeled this -> outer buildList's receiver
        }
        <info descr="null">add</info>(3)
    }
}