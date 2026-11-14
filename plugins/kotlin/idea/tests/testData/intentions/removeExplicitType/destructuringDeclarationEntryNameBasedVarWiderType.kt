// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
data class User(val name: String, val age: Int)

fun test(user: User) {
    (var a: <caret>Number = age) = user
    a = 0.5
    println(a)
}