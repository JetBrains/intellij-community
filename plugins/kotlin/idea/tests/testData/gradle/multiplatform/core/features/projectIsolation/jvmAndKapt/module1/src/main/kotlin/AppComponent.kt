import dagger.Component

@Component
interface AppComponent {
    fun <!LINE_MARKER("descr='getModule1() exposes Module1(Dependency)'")!>getModule1<!>(): Module1
}
