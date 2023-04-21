package transitiveStory.midActual.commonSource

val moduleName = "bottom-mpp"
expect val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] modules'")!>sourceSetName<!>: String

expect open class <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] modules'")!>SomeMPPInTheCommon<!>() {
    val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] modules'")!>simpleVal<!>: Int

    companion object <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] modules'")!>Compainon<!> {
        val <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, multimod-hmpp.bottom-mpp.jvm11Main, multimod-hmpp.bottom-mpp.jvmWithJavaMain] modules'")!>inTheCompanionOfBottomActualDeclarations<!>: String
    }
}

fun regularTLfunInTheMidActualCommmon(s: String): String {
    return "I'm a function at the top level of a file in `commonMain` source set of module $moduleName." +
            "This is the message I've got: \n`$s`"
}
