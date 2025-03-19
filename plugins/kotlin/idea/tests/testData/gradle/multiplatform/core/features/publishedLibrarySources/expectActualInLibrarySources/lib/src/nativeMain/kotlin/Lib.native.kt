package com.example.lib

actual object <!LINE_MARKER("descr='Has expects in commonMain module'")!>Lib<!> {

    actual fun <!LINE_MARKER("descr='Has expects in commonMain module'")!>foo<!>(): Int = 42
}

expect val <!LINE_MARKER("descr='Has actuals in [iosarm64, linuxx64] modules'; targets=[(text=iosarm64; icon=nodes/ppLibFolder.svg); (text=linuxx64; icon=nodes/ppLibFolder.svg)]")!>topLevelVal<!>: String
