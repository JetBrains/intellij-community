// PRIORITY: LOW
// AFTER-WARNING: The value 'fun () {<br>    }' assigned to 'val f: () -> Unit defined in test' is never used
// AFTER-WARNING: Variable 'f' is assigned but never accessed
fun test() {
    <caret>val f = fun () {
    }
}