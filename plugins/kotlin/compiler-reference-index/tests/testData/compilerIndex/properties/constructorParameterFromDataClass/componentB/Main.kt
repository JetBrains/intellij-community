data class Main(val a: String = "", val b<caret>: String = "") {
    fun test() {
        println(b)
    }
}