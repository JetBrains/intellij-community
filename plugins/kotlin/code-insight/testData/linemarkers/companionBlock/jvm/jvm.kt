package sample

actual class <!LINE_MARKER("descr='Has expects in common module'")!>LibClass<!> actual constructor() {
    actual fun <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Implements function in LibClass (sample) Press ... to navigate'")!>member<!>() {}

    companion {
        actual fun <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Implements function in LibClass (sample) Press ... to navigate'")!>foo<!>() {}
        actual val <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Implements property in LibClass (sample) Press ... to navigate'")!>bar<!>: Int = 42
    }
}
