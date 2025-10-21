// PRIORITY: HIGH
// INTENTION_TEXT: "Import members from 'pack.name.Fixtures'"

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