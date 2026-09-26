plugins {
    kotlin("multiplatform")
}

kotlin {
    iosSimulatorArm64()
    iosArm64()

    swiftPMDependencies {
        localSwiftPackage(
            directory = layout.projectDirectory.dir("sub/localPackage"),
            products = listOf(product("localPackage")),
        )
        localSwiftPackage(
            directory = layout.projectDirectory.dir("sub/localPackage2"),
            products = listOf(product("localPackage2")),
        )
    }
}
