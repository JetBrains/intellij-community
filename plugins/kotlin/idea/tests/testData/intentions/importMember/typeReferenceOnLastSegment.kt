// PRIORITY: HIGH
// INTENTION_TEXT: "Add import for 'pack.name.Fixtures.Register.Domain.UserRepository'"
// AFTER-WARNING: Variable 'ur' is never used

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
    val ur: pack.name.Fixtures.Register.Domain.User<caret>Repository? = null
}
