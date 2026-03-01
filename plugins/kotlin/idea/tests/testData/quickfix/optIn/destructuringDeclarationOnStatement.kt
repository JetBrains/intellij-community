// "Opt in for 'MyOptIn' on statement" "false"
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
data class OptInData(val a: String, val b: String)

fun reproduceIssue() {
    val (x, y) = <caret>OptInData("1", "2")
}

// IGNORE_K1