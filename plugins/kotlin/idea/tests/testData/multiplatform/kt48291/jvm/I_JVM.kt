package sample

actual interface <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is implemented by CommonMainImpl [jvm] (sample) Press ... to navigate'")!>CommonMainInterface<!> {
    actual val <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is implemented in CommonMainImpl [jvm] (sample) Press ... to navigate'")!>propertyFromInterface<!>: Int
}

actual class <!LINE_MARKER("descr='Has expects in common module'")!>CommonMainImpl<!> : CommonMainInterface {
    override val <!LINE_MARKER("descr='Implements property in CommonMainInterface (sample) Press ... to navigate'")!>propertyFromInterface<!>: Int = 42
    actual val <!LINE_MARKER("descr='Has expects in common module'")!>propertyFromImpl<!>: Int = 42
}
