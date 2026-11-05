// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
import java.util.function.Consumer

interface WritableConsumer {
    fun accept(t: String)
}

open class Middle : Consumer<String>, WritableConsumer {
    override fun accept(t: String) {}
}

class Leaf : Middle() {
    override fun accept(<caret>t: String) {}
}
