object MultiplatformAndroidJvmIosLibrary2_test {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidMain.sayHello()

        MultiplatformAndroidLibrary_commonMain.sayHello()
        MultiplatformAndroidLibrary_androidMain.sayHello()
    }
}