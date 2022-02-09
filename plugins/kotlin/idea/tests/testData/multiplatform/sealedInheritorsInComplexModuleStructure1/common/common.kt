package foo

expect sealed class <!LINE_MARKER("descr='Has actuals in intermediate module'"), LINE_MARKER("descr='Is subclassed by SealedWithPlatformActuals [common] SealedWithPlatformActuals [main] SimpleShared  Click or press ... to navigate'")!>SealedWithSharedActual<!>()
expect sealed class <!LINE_MARKER("descr='Has actuals in main module'"), LINE_MARKER("descr='Is subclassed by SimpleShared  Click or press ... to navigate'")!>SealedWithPlatformActuals<!> : SealedWithSharedActual
