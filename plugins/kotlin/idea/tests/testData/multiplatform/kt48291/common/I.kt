package sample

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by CommonMainImpl [common] (sample) CommonMainImpl [jvm] (sample) Press ... to navigate'")!>CommonMainInterface<!> {
    val <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is implemented in CommonMainImpl [common] (sample) CommonMainImpl [jvm] (sample) Press ... to navigate'")!>propertyFromInterface<!>: Int
}

expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>CommonMainImpl<!> : CommonMainInterface {
    val <!LINE_MARKER("descr='Has actuals in jvm module'")!>propertyFromImpl<!>: Int
    override val <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Implements property in CommonMainInterface (sample) Press ... to navigate'")!>propertyFromInterface<!>: Int
}
