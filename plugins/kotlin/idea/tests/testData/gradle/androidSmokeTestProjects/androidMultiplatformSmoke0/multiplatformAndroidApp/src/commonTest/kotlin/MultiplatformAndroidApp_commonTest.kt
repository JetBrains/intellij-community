import okio.Path.Companion.toPath

object MultiplatformAndroidApp_commonTest {
    fun sayHello() {
        "some/path/".toPath()

        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        //MultiplatformAndroidJvmIosLibrary_androidMain.sayHello() // unresolved; would be desirable

        MultiplatformJvmLibrary_commonMain.sayHello()
        MultiplatformJvmLibrary_jvmMain.sayHello()

        MultiplatformAndroidLibrary_commonMain.sayHello()
        //MultiplatformAndroidLibrary_androidMain.sayHello() // unresolved; would be desirable
    }
}
