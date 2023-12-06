//region Test configuration
// - hidden: line markers
//endregion
object JvmLibrary_test {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_jvmMain.sayHello()

        MultiplatformJvmLibrary_commonMain.sayHello()
        MultiplatformJvmLibrary_jvmMain.sayHello()
    }
}
