// RUNTIME_WITH_FULL_JDK
import java.util.logging.Logger

class A {
    class B {
        val logger = Logger.getLogger(<caret>A::class.java.name)
    }
}
