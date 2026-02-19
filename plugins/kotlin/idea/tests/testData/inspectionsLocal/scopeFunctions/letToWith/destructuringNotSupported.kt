// WITH_STDLIB
// PROBLEM: none

data class UserData(val name: String, val age: Int)

fun test(data: UserData) {
    // Should NOT suggest conversion because destructuring parameters cannot be converted to 'with'
    val result = data.<caret>let { (name, age) ->
        "User $name, age $age"
    }
}