expect sealed class <!LINE_MARKER("descr='Has actuals in [platformAY, platformAX] modules'"), LINE_MARKER("descr='Is subclassed by CommonAImplTestClass CommonImplTestClass PlatformAXImplTestClass PlatformAYImplTestClass Press ... to navigate'")!>TestClass<!>()
class CommonImplTestClass: TestClass()


fun checkCommon(t: TestClass): Int = <!EXPECT_TYPE_IN_WHEN_WITHOUT_ELSE, NO_ELSE_IN_WHEN!>when<!> (t) {
    is CommonImplTestClass -> 0
}
