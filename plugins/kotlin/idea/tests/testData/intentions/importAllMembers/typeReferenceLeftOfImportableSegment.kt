// PRIORITY: HIGH
// INTENTION_TEXT: "Import members from 'pack.name.Fixtures'"
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
    val ur: pack.name.Fix<caret>tures.Register.Domain.UserRepository? = null
}
