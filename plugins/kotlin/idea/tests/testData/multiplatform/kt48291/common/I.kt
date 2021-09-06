package sample

expect interface <!LINE_MARKER("descr='Has actuals in JVM'"), LINE_MARKER("descr='Is subclassed by CommonMainImpl [common] CommonMainImpl [jvm]  Click or press ... to navigate'")!>CommonMainInterface<!> {
    val <!LINE_MARKER("descr='Has actuals in JVM'"), LINE_MARKER("descr='Is implemented in sample.CommonMainImpl'")!>propertyFromInterface<!>: Int
}

expect class <!LINE_MARKER("descr='Has actuals in JVM'")!>CommonMainImpl<!> : CommonMainInterface {
    val <!LINE_MARKER("descr='Has actuals in JVM'")!>propertyFromImpl<!>: Int
}
