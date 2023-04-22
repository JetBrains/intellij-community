// FIR_COMPARISON
import java.io.File

class A: File("") {
    fun foo() {
        super.<caret>
    }
}

// EXIST: {"lookupString":"getAbsolutePath","tailText":"()", "typeText":"String"}
// ABSENT: absolutePath