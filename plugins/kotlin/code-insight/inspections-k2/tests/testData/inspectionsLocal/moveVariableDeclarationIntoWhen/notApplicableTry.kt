// PROBLEM: none
fun foo() {
    val a<caret> = try { 1 } catch (e: Exception) { 2 }
    when (a) {
        1 -> {
        }
        else -> {
        }
    }
}