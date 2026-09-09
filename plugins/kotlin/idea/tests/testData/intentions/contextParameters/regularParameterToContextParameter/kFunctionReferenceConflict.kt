// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference '::fn' cannot be updated automatically because parameters were converted to/from context parameters
import kotlin.reflect.KFunction2

class Ctx

fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun usage() {
    val ref: KFunction2<Ctx, Int, String> = ::fn
}
