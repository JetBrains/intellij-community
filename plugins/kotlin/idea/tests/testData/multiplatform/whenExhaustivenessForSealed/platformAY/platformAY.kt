actual sealed class <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is subclassed by PlatformAYImplTestClass  Click or press ... to navigate'")!>TestClass<!> actual constructor() {}
class PlatformAYImplTestClass: TestClass()

fun checkCommonAY(t: TestClass): Int = when (t) {
    is CommonImplTestClass -> 0
    is CommonAImplTestClass -> 1
    is PlatformAYImplTestClass -> 2
}
