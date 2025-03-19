// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

actual class <!LINE_MARKER("descr='Has expects in common module'")!>Sample<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>checkMe<!>() = 42
}

actual object <!LINE_MARKER("descr='Has expects in common module'")!>Platform<!> {
    actual val <!LINE_MARKER("descr='Has expects in common module'")!>name<!>: String = "JVM"
}

actual fun <!LINE_MARKER("descr='Has expects in common module'")!>foo<!>() {}

annotation class PreviewImpl

actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>Preview<!> = PreviewImpl

annotation object SomeMyObject

actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>SomeObject<!> = SomeMyObject

actual interface <!LINE_MARKER("descr='Has expects in common module'")!>WithCompanion<!> {
    actual companion <!LINE_MARKER("descr='Has expects in common module'")!>object<!> {}
}
