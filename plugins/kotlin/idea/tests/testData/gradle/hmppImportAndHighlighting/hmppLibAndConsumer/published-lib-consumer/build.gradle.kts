plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

repositories {
    run {
        val publishedLibRepoPath = "$rootDir/../lib-and-app/repo"
        if (!file(publishedLibRepoPath).isDirectory) {
            logger.warn(
					"\nThis module needs the lib from `lib-and-app` to be published to $publishedLibRepoPath." +
							"\nPlease run the `publish` task in the `lib-and-app` project."
            )
        }
		maven(publishedLibRepoPath)
    }

    {{kts_kotlin_plugin_repositories}}
}

group = "com.h0tk3y.mpp.demo"
version = "1.0"

kotlin {
    jvm()
    js(IR)

    ios()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.h0tk3y.mpp.demo:lib:1.0")
                implementation(kotlin("stdlib-common"))
            }
        }

        val jvmAndJsMain by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependsOn(jvmAndJsMain)
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }

        val jsMain by getting {
            dependsOn(jvmAndJsMain)
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
    }
}

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}