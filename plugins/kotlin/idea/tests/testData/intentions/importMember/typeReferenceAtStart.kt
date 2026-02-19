// PRIORITY: HIGH
// INTENTION_TEXT: "Add import for 'pack.name.Fixtures.Register'"

package pack.name

class Fixtures {
    class Register {
        class Domain {
            object UserRepository {
                val authSuccess = true
                val authError = false
            }
        }
    }
}

fun test() {
    val ur: <caret>pack.name.Fixtures.Register.Domain.UserRepository? = null
}

// IGNORE_K1