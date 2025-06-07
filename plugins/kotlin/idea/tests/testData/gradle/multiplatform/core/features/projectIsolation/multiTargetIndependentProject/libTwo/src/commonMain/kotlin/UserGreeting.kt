expect fun <!LINE_MARKER("descr='Has actuals in [multiTargetIndependentProject.libTwo.jvmMain, multiTargetIndependentProject.libTwo.mingwMain] modules'; targets=[(text=multiTargetIndependentProject.libTwo.jvmMain); (text=multiTargetIndependentProject.libTwo.mingwMain)]")!>getUserPlatformInfo<!>(): String

fun personalizedGreeting(name: String): String {
    return "Hello, $name! " + getUserPlatformInfo()
}
