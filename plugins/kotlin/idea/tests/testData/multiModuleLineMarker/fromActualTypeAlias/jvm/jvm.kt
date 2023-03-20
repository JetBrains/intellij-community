class Some {
    val x: String = "Some"
}

actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>TypeAlias<!> = Some
