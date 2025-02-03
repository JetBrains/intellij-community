import javax.inject.Inject

class Dependency @Inject <!LINE_MARKER("descr='Dependency() provides for Module1'")!>constructor<!>() {
    fun provideInfo(): String = "I'm a dependency!"
}
