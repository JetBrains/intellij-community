// WITH_STDLIB
fun test(): String {
    <caret>try {
        try {
            return "success"
        } catch (e: Exception) {
            TODO()
        }
    } catch (e: Exception) {
        try {
            TODO()
        } catch (e: Exception) {
            return "failure"
        }
    }
}