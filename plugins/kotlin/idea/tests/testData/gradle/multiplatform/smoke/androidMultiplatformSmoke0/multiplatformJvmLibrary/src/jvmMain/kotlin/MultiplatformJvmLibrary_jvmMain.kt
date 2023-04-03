//region Test configuration
// - hidden: line markers
//endregion
import okio.Path.Companion.toPath

object MultiplatformJvmLibrary_jvmMain {
    fun sayHello() {
        "".toPath().toFile()

        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_jvmMain.sayHello()
    }
}
