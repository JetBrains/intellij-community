// SHOULD_FAIL_WITH: Callable reference '::fn' cannot pass an explicit argument to context parameter 'ctx'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
import kotlin.reflect.KFunction2

class Ctx

fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun usage() {
    val ref: KFunction2<Ctx, Int, String> = ::fn
}
