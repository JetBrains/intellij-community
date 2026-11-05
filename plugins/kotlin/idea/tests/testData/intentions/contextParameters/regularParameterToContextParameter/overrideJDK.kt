// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
import java.util.function.Consumer

class MyConsumer : Consumer<String> {
    override fun accept(<caret>t: String) {}
}