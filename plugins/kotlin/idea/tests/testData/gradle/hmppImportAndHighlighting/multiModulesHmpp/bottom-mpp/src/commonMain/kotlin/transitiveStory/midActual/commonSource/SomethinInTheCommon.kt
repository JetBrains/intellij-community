package transitiveStory.midActual.commonSource

val moduleName = "bottom-mpp"
expect val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] module'")!>sourceSetName<!>: String

expect open class <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] module'")!>SomeMPPInTheCommon<!>() {
    val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] module'")!>simpleVal<!>: Int

    companion object <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] module'")!>Compainon<!> {
        val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] module'")!>inTheCompanionOfBottomActualDeclarations<!>: String
    }
}

fun regularTLfunInTheMidActualCommmon(s: String): String {
    return "I'm a function at the top level of a file in `commonMain` source set of module $moduleName." +
            "This is the message I've got: \n`$s`"
}
