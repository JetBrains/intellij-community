package com.example.lib

expect object <!LINE_MARKER("descr='Has actuals in [iosarm64, jvm, linuxx64] modules'; targets=[(text=iosarm64; icon=nodes/ppLibFolder.svg); (text=jvm; icon=nodes/ppLibFolder.svg); (text=linuxx64; icon=nodes/ppLibFolder.svg)]")!>Lib<!> {

    fun <!LINE_MARKER("descr='Has actuals in [iosarm64, jvm, linuxx64] modules'; targets=[(text=iosarm64; icon=nodes/ppLibFolder.svg); (text=jvm; icon=nodes/ppLibFolder.svg); (text=linuxx64; icon=nodes/ppLibFolder.svg)]")!>foo<!>(): Int
}

expect fun <!LINE_MARKER("descr='Has actuals in [iosarm64, jvm, linuxx64] modules'; targets=[(text=iosarm64; icon=nodes/ppLibFolder.svg); (text=jvm; icon=nodes/ppLibFolder.svg); (text=linuxx64; icon=nodes/ppLibFolder.svg)]")!>topLevelFun<!>()
