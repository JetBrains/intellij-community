// WITH_STDLIB
// FIX: Convert to 'with'
// IGNORE_K1
class User(val name: String, val age: Int) {
    fun isEmpty(): Boolean = name.isEmpty()
}

fun processUser(user: User): String? {
    return user.<caret>let label@ { 
        if (it.isEmpty()) return@label null
        "User: ${it.name}"
    }
}