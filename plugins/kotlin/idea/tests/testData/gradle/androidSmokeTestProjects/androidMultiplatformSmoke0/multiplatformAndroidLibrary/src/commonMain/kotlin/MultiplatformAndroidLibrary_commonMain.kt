import okio.Path.Companion.toPath

object MultiplatformAndroidLibrary_commonMain {
    fun sayHello() {
        "path/to/file".toPath()
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidMain.sayHello()
    }
}