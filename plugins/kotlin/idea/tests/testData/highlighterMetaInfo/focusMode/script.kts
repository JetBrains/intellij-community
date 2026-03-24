// IGNORE_K1
// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import kotlin.reflect.full.declaredMembers

val scriptArgs = args

println("Hello from simple.kts! Args=$scriptArgs")

val ktsScriptClassMembers = this::class.declaredMembers

fun foo() {
    val value = 1
}
