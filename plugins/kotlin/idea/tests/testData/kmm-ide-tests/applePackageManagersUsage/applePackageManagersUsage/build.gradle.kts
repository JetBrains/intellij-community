plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
}

kotlin {
    iosSimulatorArm64()
    iosArm64()

    cocoapods {
        version = "1.0.0"
        pod("foo")
    }

    swiftPMDependencies {
        swiftPackage(
            url = "bar",
            version = "1.2.3",
            products = listOf("baz")
        )
    }
}
