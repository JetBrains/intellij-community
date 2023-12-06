// FIR_COMPARISON
import java.io.*

class A : File("") {
    fun test() {
        absolu<caret>
    }
}

// EXIST: {"lookupString":"absolutePath","tailText":" (from getAbsolutePath())","typeText":"String!"}
// ABSENT: getAbsolutePath