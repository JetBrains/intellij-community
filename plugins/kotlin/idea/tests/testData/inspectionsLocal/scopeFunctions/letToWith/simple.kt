// WITH_STDLIB
// FIX: Convert to 'with'
// IGNORE_K1
class User(val name: String, val age: Int)

fun test(user: User) {
    val a = user.<caret>let {
        "User ${it.name}, age ${it.age}"
    }
}