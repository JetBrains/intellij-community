// API_VERSION: 1.5
// WITH_RUNTIME
data class User(val id: Long, val name: String?)

fun test(users: List<User>) {
    users.<caret>mapNotNull { it.name }.firstOrNull()
}