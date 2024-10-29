plugins {
    kotlin("multiplatform")
}
allprojects {
    repositories {
        {{ kts_kotlin_plugin_repositories }}
    }
}

kotlin {
    jvm()
    linuxX64 {
        compilations.getByName("main").cinterops.create("myInterop")
    }
}

