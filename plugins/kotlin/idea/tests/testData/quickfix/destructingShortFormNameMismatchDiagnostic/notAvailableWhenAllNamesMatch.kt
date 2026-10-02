// "Convert to a full name-based destructuring form" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// WITH_STDLIB

data class User(val name: String, val age: Int)

fun test(user: User) {
    val (<caret>name, age) = user
}
