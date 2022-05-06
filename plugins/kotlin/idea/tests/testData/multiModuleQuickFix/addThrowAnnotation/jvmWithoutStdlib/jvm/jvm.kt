// "Add '@Throws' annotation" "false"
// ACTION: Do not show return expression hints

fun test() {
    <caret>throw Throwable()
}