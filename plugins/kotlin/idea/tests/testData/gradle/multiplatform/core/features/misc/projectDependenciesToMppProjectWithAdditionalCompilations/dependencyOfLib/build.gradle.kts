plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        val main by compilations.getting
        val namesake by compilations.creating // used in jar, matches by name with the compilation from libMpp

        // add outputs of the custom compilation to the resulting target JAR (will be attached to target's consumable configurations)
        tasks.named<Jar>(artifactsTaskName) {
            from(namesake.output.allOutputs)
        }
    }
    iosArm64()
}