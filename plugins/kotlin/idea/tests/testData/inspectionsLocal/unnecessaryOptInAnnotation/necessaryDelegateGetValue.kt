// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
import kotlin.reflect.KProperty

@RequiresOptIn
annotation class DelegateOptIn

class Delegate {
    @DelegateOptIn
    operator fun getValue(instance: Any?, property: KProperty<*>): String = ""
}

@OptIn(DelegateOptIn::class)<caret>
val foo by Delegate()
