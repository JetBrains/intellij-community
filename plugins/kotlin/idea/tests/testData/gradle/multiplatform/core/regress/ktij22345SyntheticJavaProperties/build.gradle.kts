plugins {
    kotlin("multiplatform") version "{{kgp_version}}"
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        withJava()
    }
}
