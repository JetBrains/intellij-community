// WITH_STDLIB
// FIX: Convert to 'let'
// IGNORE_K1
class User(val name: String, val age: Int)

fun test(user: User) {
    val a = <caret>with(user) {
        "User $name, age $age"
    }
}