//region Test configuration
// - hidden: line markers
//endregion
object MultiplatformAndroidJvmIosLibrary2_jvmTest {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_jvmMain.sayHello()

        MultiplatformJvmLibrary_commonMain.sayHello()
        MultiplatformJvmLibrary_jvmMain.sayHello()

        JvmLibrary_main.sayHello()
    }
}
