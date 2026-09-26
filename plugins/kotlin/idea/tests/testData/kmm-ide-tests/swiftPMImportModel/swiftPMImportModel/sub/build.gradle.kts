plugins {
    kotlin("multiplatform")
}

kotlin {
    iosSimulatorArm64()
    iosArm64()

    swiftPMDependencies {
        localSwiftPackage(
            directory = layout.projectDirectory.dir("localPackage"),
            products = listOf(product("localPackage")),
        )
    }
}
