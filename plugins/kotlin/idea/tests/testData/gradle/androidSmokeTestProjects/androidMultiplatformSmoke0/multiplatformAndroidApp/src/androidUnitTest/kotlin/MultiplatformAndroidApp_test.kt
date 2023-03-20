import okio.Path.Companion.toPath

object MultiplatformAndroidApp_test {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidLibrary_androidMain.sayHello()
        "".toPath()
    }
}