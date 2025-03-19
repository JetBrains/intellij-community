// LANGUAGE_VERSION: 1.4
// ERROR: Local variables are not allowed to have type parameters
fun main(args: Array<String>) {
    val <T : __UNRESOLVED__><caret> x = ""
}