// "Add empty argument list" "true"
fun suspend(fn: () -> Unit) {}

fun callSuspend() {
    suspend<caret> {  }
}