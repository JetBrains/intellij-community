// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
import kotlin.reflect.KProperty

@RequiresOptIn
annotation class DelegateOptIn

class Delegate {
    operator fun getValue(instance: Any?, property: KProperty<*>): String = ""
}

@DelegateOptIn
val delegate = Delegate()

@OptIn(DelegateOptIn::class)<caret>
val foo by delegate
