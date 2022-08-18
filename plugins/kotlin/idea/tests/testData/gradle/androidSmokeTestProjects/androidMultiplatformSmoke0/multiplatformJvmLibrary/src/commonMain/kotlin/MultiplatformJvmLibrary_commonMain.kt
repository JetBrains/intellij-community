import okio.Path.Companion.toPath

object MultiplatformJvmLibrary_commonMain {
    fun sayHello() {
        "".toPath().toFile()

        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_jvmMain.sayHello()
    }
}