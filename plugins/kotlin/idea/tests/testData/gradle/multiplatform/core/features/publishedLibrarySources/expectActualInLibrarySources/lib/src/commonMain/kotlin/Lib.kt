package com.example.lib

expect object <!LINE_MARKER("descr='Has actuals in [iosarm64, jvm, linuxx64] modules'")!>Lib<!> {

    fun <!LINE_MARKER("descr='Has actuals in [iosarm64, jvm, linuxx64] modules'")!>foo<!>(): Int
}

expect fun <!LINE_MARKER("descr='Has actuals in [iosarm64, jvm, linuxx64] modules'")!>topLevelFun<!>()
