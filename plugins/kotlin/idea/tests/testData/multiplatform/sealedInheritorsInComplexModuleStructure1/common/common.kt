package foo

expect sealed class <!LINE_MARKER("descr='Has actuals in intermediate module'"), LINE_MARKER("descr='Is subclassed by SealedWithPlatformActuals [common] (foo) SealedWithPlatformActuals [main] (foo) SimpleShared (foo) Press ... to navigate'")!>SealedWithSharedActual<!>()
expect sealed class <!LINE_MARKER("descr='Has actuals in main module'"), LINE_MARKER("descr='Is subclassed by SimpleShared (foo) Press ... to navigate'")!>SealedWithPlatformActuals<!> : SealedWithSharedActual
