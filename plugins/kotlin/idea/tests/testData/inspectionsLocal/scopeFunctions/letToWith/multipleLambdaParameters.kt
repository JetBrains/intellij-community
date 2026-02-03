// WITH_STDLIB  
// PROBLEM: none

class User(val name: String, val age: Int)

fun test(user: User) {
    // Should NOT suggest conversion because explicit parameters prevent conversion
    val result = user.<caret>let { userData ->
        "User ${userData.name}, age ${userData.age}"
    }
}