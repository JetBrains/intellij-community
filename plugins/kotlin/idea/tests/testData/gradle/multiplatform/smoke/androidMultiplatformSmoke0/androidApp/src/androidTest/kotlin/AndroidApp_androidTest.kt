//region Test configuration
// - hidden: line markers
//endregion
object AndroidApp_androidTest {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidMain.sayHello()

        MultiplatformAndroidLibrary_commonMain.sayHello()
        MultiplatformAndroidLibrary_androidMain.sayHello()

        MultiplatformJvmLibrary_commonMain.sayHello()
        MultiplatformJvmLibrary_jvmMain.sayHello()
    }
}
