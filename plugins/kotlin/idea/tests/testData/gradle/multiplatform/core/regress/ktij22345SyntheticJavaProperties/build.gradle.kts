plugins {
    kotlin("multiplatform") version "{{kgp_version}}"
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    /* Waiting for compilerOptions API to get back */
    @Suppress("INVISIBLE_MEMBER")
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        withJava()
    }
}
