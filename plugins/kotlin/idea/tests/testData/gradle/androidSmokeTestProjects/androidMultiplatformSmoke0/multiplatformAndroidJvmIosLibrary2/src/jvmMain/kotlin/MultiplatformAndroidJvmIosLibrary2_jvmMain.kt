object MultiplatformAndroidJvmIosLibrary2_jvmMain {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_jvmMain.sayHello()

        MultiplatformJvmLibrary_commonMain.sayHello()
        MultiplatformJvmLibrary_jvmMain.sayHello()

        JvmLibrary_main.sayHello()
    }
}