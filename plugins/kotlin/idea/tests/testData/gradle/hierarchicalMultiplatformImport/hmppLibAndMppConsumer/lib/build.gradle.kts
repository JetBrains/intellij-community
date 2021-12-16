plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

group = "com.h0tk3y.mpp.demo"
version = "1.0"

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    jvm()
    js {
        browser()
    }
    linuxX64()

    sourceSets {
        val commonMain by getting {
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmAndJsMain by creating {
            dependsOn(commonMain)
        }

        val jvmAndJsTest by creating {
            dependsOn(commonTest)
        }

        val jvmMain by getting {
            dependsOn(jvmAndJsMain)
        }

        val jvmTest by getting {
            dependsOn(jvmAndJsTest)
        }

        val jsMain by getting {
            dependsOn(jvmAndJsMain)
        }

        val jsTest by getting {
            dependsOn(jvmAndJsTest)
        }
    }
}

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}