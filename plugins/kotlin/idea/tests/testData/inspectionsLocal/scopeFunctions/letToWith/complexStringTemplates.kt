// WITH_STDLIB
// FIX: Convert to 'with'
// IGNORE_K1
class User(val name: String, val age: Int) {
    fun getName(): String = name.uppercase()
    fun getAge(): Int = age
    fun isAdult(): Boolean = age >= 18
}

fun test(user: User) {
    val result = user.<caret>let {
        "User ${it.getName()} (${it.getAge()} years old) - Adult: ${it.isAdult()}"
    }
}