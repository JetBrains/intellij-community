package transitiveStory.bottomActual.mppBeginning

actual open class <!LINE_MARKER("descr='Has declaration in common module'"), LINE_MARKER("descr='Has subclasses'")!>BottomActualDeclarations<!> {
    actual val <!LINE_MARKER("descr='Has declaration in common module'")!>simpleVal<!>: Int = commonInt

    actual companion object <!LINE_MARKER("descr='Has declaration in common module'")!>Compainon<!> {
        actual val <!LINE_MARKER("descr='Has declaration in common module'")!>inTheCompanionOfBottomActualDeclarations<!>: String =
            "I'm a string from the companion object of `$this` in `$sourceSetName` module `$moduleName`"
    }
}

