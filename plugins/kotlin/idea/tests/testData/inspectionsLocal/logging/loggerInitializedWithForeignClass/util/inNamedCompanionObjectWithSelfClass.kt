// PROBLEM: none
// RUNTIME_WITH_FULL_JDK
import java.util.logging.Logger

class B {
    companion object C {
        val logger = Logger.getLogger(<caret>B::class.java.name)
    }
}
