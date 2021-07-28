// PROBLEM: none
// RUNTIME_WITH_FULL_JDK
import java.util.logging.Logger

class B {
    companion object {
        val logger = Logger.getLogger(<caret>Companion::class.java.name)
    }
}
