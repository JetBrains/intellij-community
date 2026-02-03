// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
import kotlin.reflect.KProperty

@RequiresOptIn
annotation class DelegateOptIn

@DelegateOptIn
operator fun String.provideDelegate(instance: Any?, property: KProperty<*>) = lazy { this }

@OptIn(DelegateOptIn::class)<caret>
val foo by ""