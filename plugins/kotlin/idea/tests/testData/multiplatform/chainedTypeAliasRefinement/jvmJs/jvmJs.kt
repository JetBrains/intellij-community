expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm); (text=js)]")!>JvmJs<!> : Number {
    override fun <!LINE_MARKER("descr='Press ... to navigate'")!>toDouble<!>(): Double
    override fun <!LINE_MARKER("descr='Press ... to navigate'")!>toFloat<!>(): Float
    override fun <!LINE_MARKER("descr='Press ... to navigate'")!>toLong<!>(): Long
    override fun <!LINE_MARKER("descr='Press ... to navigate'")!>toInt<!>(): Int
    override fun <!LINE_MARKER("descr='Press ... to navigate'")!>toShort<!>(): Short
    override fun <!LINE_MARKER("descr='Press ... to navigate'")!>toByte<!>(): Byte
}
actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>Common<!> = JvmJs
