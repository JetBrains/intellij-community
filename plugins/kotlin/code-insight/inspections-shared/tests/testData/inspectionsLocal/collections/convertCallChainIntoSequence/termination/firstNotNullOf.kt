// WITH_STDLIB
data class User(val id: Long, val name: String?)

fun test(users: List<User>) {
    users.<caret>filter { it.id > 1 }.firstNotNullOf { it.name }
}