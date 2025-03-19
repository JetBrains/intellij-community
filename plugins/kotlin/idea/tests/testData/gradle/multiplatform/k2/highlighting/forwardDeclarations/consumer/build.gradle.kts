plugins {
    kotlin("multiplatform")
}

kotlin {
    linuxX64().createCInterop("myInterop")
    mingwX64().createCInterop("myInterop")
    macosX64().createCInterop("myInterop")
}

fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.createCInterop(name: String) {
    compilations.getByName("main").cinterops.create(name)
}
