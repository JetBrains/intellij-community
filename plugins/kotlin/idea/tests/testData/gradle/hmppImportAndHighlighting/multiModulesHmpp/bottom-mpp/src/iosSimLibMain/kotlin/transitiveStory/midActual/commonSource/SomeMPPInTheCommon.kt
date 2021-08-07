package transitiveStory.midActual.commonSource

actual open class <!LINE_MARKER("descr='Has declaration in common module'")!>SomeMPPInTheCommon<!> actual constructor() {
    actual val <!LINE_MARKER("descr='Has declaration in common module'")!>simpleVal<!>: Int = 85

    actual companion object <!LINE_MARKER("descr='Has declaration in common module'")!>Compainon<!> {
        actual val <!LINE_MARKER("descr='Has declaration in common module'")!>inTheCompanionOfBottomActualDeclarations<!>: String = "I'm the string in `$sourceSetName` source set"
    }

}

actual val <!LINE_MARKER("descr='Has declaration in common module'")!>sourceSetName<!>: String = "iosSimLibMain"
