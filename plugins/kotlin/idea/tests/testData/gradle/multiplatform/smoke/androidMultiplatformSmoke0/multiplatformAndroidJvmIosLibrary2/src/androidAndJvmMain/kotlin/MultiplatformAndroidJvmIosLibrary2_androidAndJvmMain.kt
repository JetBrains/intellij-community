//region Test configuration
// - hidden: line markers
//endregion
object MultiplatformAndroidJvmIosLibrary2_androidAndJvmMain {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
    }
}
