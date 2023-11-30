plugins {
    kotlin("multiplatform")
}

kotlin {

    targets.all {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    jvm()
    ios()
}
