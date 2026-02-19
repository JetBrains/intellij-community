import javax.inject.Inject

class Module1 @Inject <!LINE_MARKER("descr='Module1(Dependency) exposed in AppComponent'")!>constructor<!>(private val <!LINE_MARKER("descr='dependency consumes Dependency()'")!>dependency<!>: Dependency) {
    fun greet(): String = "Hello from Module1 and ${dependency.provideInfo()}"
}
