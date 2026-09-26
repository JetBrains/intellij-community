expect fun <!LINE_MARKER("descr='Has actuals in [multiTargetIndependentProject.libOne.appleMain, multiTargetIndependentProject.libOne.jvmMain, multiTargetIndependentProject.libOne.linuxX64Main] modules'; targets=[(text=multiTargetIndependentProject.libOne.appleMain); (text=multiTargetIndependentProject.libOne.jvmMain); (text=multiTargetIndependentProject.libOne.linuxX64Main)]")!>writeLogMessage<!>(message: String)

fun add(num1: Double, num2: Double): Double {
    val sum = num1 + num2
    writeLogMessage("The sum of $num1 and $num2 is $sum")
    return sum
}
