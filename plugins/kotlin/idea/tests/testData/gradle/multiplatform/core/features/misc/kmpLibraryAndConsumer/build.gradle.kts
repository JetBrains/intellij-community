repositories {
    run {
        val publishedLibRepoPath = "$rootDir/repo"
        if (!file(publishedLibRepoPath).isDirectory) {
            logger.error(
                "\nThis module needs the lib from `lib-and-app` to be published to $publishedLibRepoPath." +
                        "\nPlease run the `publish` task in the `lib-and-app` project."
            )
        }
        maven(publishedLibRepoPath)
    }

    {{kts_kotlin_plugin_repositories}}
}

plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    {{iosTargetPlaceholder}}

    sourceSets.getByName("commonMain") {
        dependencies {
            implementation("com.h0tk3y.mpp.demo:lib:1.0")
        }
    }
}
