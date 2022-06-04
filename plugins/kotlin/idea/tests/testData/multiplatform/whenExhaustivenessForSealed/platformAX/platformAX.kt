actual sealed class <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is subclassed by PlatformAXImplTestClass  Click or press ... to navigate'")!>TestClass<!> actual constructor() {}
class PlatformAXImplTestClass: TestClass()

fun checkCommonAX(t: TestClass): Int = when (t) {
    is CommonImplTestClass -> 0
    is CommonAImplTestClass -> 1
    is PlatformAXImplTestClass -> 2
}
