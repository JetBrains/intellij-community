package transitiveStory.midActual.commonSource


actual open class <!LINE_MARKER("descr='Has expects in multimod-hmpp.bottom-mpp.commonMain module'")!>SomeMPPInTheCommon<!> actual constructor() {
    actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.bottom-mpp.commonMain module'")!>simpleVal<!>: Int = 16

    actual companion object <!LINE_MARKER("descr='Has expects in multimod-hmpp.bottom-mpp.commonMain module'")!>Compainon<!> {
        actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.bottom-mpp.commonMain module'")!>inTheCompanionOfBottomActualDeclarations<!>: String = "I'm the string in `$sourceSetName` source set"
    }

}

actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.bottom-mpp.commonMain module'")!>sourceSetName<!>: String = "jvm16Main"
