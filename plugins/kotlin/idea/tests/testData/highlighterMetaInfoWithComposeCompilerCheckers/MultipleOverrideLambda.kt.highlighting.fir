// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.Composable

class Impl : <!HIGHLIGHTING("severity='ERROR'; descr='[MIXING_FUNCTIONAL_KINDS_IN_SUPERTYPES] Mixing supertypes of different functional kinds ([Function, @Composable]) is not allowed.'")!>() -> Unit, @Composable (Int) -> Unit<!> {
    @Composable override <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: fun invoke(): Unit fun invoke(): R'")!>fun invoke()<!> {}
    @Composable override fun invoke(p0: Int) {}
}
