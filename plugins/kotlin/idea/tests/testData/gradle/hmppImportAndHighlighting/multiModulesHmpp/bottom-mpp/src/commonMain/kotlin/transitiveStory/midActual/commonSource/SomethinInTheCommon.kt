package transitiveStory.midActual.commonSource

val moduleName = "bottom-mpp"
expect val <!LINE_MARKER("descr='Has actuals in Native, JVM (2 modules)'")!>sourceSetName<!>: String

expect open class <!LINE_MARKER("descr='Has actuals in Native, JVM (2 modules)'")!>SomeMPPInTheCommon<!>() {
    val <!LINE_MARKER("descr='Has actuals in Native, JVM (2 modules)'")!>simpleVal<!>: Int

    companion object <!LINE_MARKER("descr='Has actuals in Native, JVM (2 modules)'")!>Compainon<!> {
        val <!LINE_MARKER("descr='Has actuals in Native, JVM (2 modules)'")!>inTheCompanionOfBottomActualDeclarations<!>: String
    }
}

fun regularTLfunInTheMidActualCommmon(s: String): String {
    return "I'm a function at the top level of a file in `commonMain` source set of module $moduleName." +
            "This is the message I've got: \n`$s`"
}
