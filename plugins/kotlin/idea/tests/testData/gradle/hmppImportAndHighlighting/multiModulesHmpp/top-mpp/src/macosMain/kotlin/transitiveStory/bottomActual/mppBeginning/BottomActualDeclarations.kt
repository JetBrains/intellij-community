package transitiveStory.bottomActual.mppBeginning

actual open class <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>BottomActualDeclarations<!> {
    actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>simpleVal<!>: Int = commonInt

    actual companion object <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>Compainon<!> {
        actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>inTheCompanionOfBottomActualDeclarations<!>: String =
                "I'm a string from the companion object of `$this` in `$sourceSetName` module `$moduleName`"
    }
}

actual open class <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'"), LINE_MARKER("descr='Is subclassed by ChildOfMPOuterInMacos (transitiveStory.bottomActual.mppBeginning) Press ... to navigate'")!>MPOuter<!> {
    protected actual open val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>b<!>: Int = 4325
    internal actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>c<!>: Int = 2345
    actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>d<!>: Int = 325

    protected actual class <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>MPNested<!> {
        actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>e<!>: Int = 345
    }

}

class ChildOfCommonInMacos : Outer() {
    override val <!LINE_MARKER("descr='Overrides property in Outer (transitiveStory.bottomActual.mppBeginning) Press ... to navigate'")!>b<!>: Int
        get() = super.b + 243
    val callAlso = super.c // internal in Outer

    private val other = Nested()
}

class ChildOfMPOuterInMacos : MPOuter() {
    private val sav = MPNested()
}

actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>sourceSetName<!>: String = "macosMain"
