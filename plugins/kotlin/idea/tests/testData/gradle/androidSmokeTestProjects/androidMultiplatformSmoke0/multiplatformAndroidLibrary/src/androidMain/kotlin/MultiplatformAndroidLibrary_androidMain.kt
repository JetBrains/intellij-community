import okio.Path.Companion.toPath

object MultiplatformAndroidLibrary_androidMain {
    fun sayHello() {
        "path/to/file".toPath()
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidMain.sayHello()
    }
}