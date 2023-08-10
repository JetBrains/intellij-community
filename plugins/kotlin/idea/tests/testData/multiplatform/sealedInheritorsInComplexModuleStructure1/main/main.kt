package foo

actual sealed class <!LINE_MARKER("descr='Has expects in common module'")!>SealedWithPlatformActuals<!> <!ACTUAL_WITHOUT_EXPECT!>actual constructor()<!>: <!SEALED_INHERITOR_IN_DIFFERENT_MODULE!>SealedWithSharedActual<!>()
