// WITH_STDLIB
// FIX: Convert to 'with'

class User(val name: String, val age: Int)

fun test(user: User) {
    val a = user.<caret>let {
        "User ${it.name}, age ${it.age}"
    }
}