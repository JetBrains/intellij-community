plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    jvm()
    sourceSets {
        val commonMain = getByName("commonMain")

        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain)
        }

        getByName("jvmMain") {
            dependsOn(jvmAndAndroidMain)
        }

        getByName("androidMain") {
            dependsOn(jvmAndAndroidMain)
        }
    }
}
