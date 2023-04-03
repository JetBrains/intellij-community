//region Test configuration
// - hidden: line markers
//endregion
object MultiplatformAndroidJvmIosLibrary2_androidMain {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidMain.sayHello()

        MultiplatformAndroidLibrary_commonMain.sayHello()
        MultiplatformAndroidLibrary_androidMain.sayHello()
    }
}
