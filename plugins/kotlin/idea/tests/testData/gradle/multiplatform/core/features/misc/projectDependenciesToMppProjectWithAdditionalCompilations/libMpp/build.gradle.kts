plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        val main by compilations.getting
        val additionalTest by compilations.creating {
            associateWith(main)
        }
        val additionalNonTest by compilations.creating
        val namesake by compilations.creating // not used in jar, matches by name with the compilation from dependencyOfLib

        // add outputs of the custom compilation to the resulting target JAR (will be attached to target's consumable configurations)
        tasks.named<Jar>(artifactsTaskName) {
            from(additionalNonTest.output.allOutputs)
        }
    }
    iosArm64()

    sourceSets.getByName("commonMain").dependencies {
        implementation(project(":dependencyOfLib"))
    }
}