package transitiveStory.bottomActual.mppBeginning

val moduleName = "top-mpp"
val commonInt = 42
expect val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.dummyiOSMain, multimod-hmpp.top-mpp.jsMain, multimod-hmpp.top-mpp.jvm18Main, multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain] module'")!>sourceSetName<!>: String

expect open class <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] module'"), LINE_MARKER("descr='Has subclasses'")!>BottomActualDeclarations<!>() {
    val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] module'")!>simpleVal<!>: Int

    companion object <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] module'")!>Compainon<!> {
        val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] module'")!>inTheCompanionOfBottomActualDeclarations<!>: String
    }
}

fun regularTLfunInTheBottomActualCommmon(s: String): String {
    return "I'm a function at the top level of a file in `commonMain` source set of module $moduleName." +
            "This is the message I've got: \n`$s`"
}

// shouldn't be resolved
/*
fun bottActualApiCaller(k: KotlinApiContainer, s: JavaApiContainer) {
    // val first = privateKotlinDeclaration
}*/

internal val tlInternalInCommon = 42

// has a child in jsJvm18Main
open class <!LINE_MARKER("descr='Is subclassed by ChildOfCommonInMacos ChildOfCommonInShared Subclass  Click or press ... to navigate'")!>Outer<!> {
    private val a = 1
    protected open val <!LINE_MARKER("descr='Is overridden in transitiveStory.bottomActual.mppBeginning.ChildOfCommonInMacos transitiveStory.bottomActual.intermediateSrc.ChildOfCommonInShared transitiveStory.bottomActual.intermediateSrc.Subclass'")!>b<!> = 2
    internal val c = 3
    val d = 4  // public by default

    protected class Nested {
        public val e: Int = 5
    }
}

// has a child in jsJvm18Main
expect open class <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] modules'"), LINE_MARKER("descr='Is subclassed by ChildOfMPOuterInMacos ChildOfMPOuterInShared  Click or press ... to navigate'")!>MPOuter<!> {
    protected open val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] modules'")!>b<!>: Int
    internal val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] modules'")!>c<!>: Int
    val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] modules'")!>d<!>: Int // public by default

    protected class <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] modules'")!>MPNested<!> {
        public val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.linuxMain, multimod-hmpp.top-mpp.macosMain, multimod-hmpp.top-mpp.jsJvm18iOSMain] modules'")!>e<!>: Int
    }
}
