object MultiplatformAndroidJvmIosLibrary_androidMain {
    fun sayHello() {
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        println("Hello")
    }
}