// FIR_COMPARISON
import java.io.File

class A: File("") {
    fun foo() {
        super.<caret>
    }
}

// EXIST: {"lookupString":"absolutePath","tailText":" (from getAbsolutePath())","typeText":"String!"}
// ABSENT: getAbsolutePath

/* K2 allows references to synthetic Java properties after super, see KT-55551 */