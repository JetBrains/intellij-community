plugins {
    id("org.jetbrains.intellij") version "1.2.0"
}

group = "ru.adelf"
version = "2021.4"

repositories {
    mavenCentral()
}

apply {
    plugin("idea")
    plugin("org.jetbrains.intellij")
    plugin("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/gen")
        resources.srcDirs("src/main/resources")
    }
    test {
        java.srcDirs("src/test/java")
        resources.srcDirs("src/test/resources")
    }
}

intellij {
    version.set("IU-212.4746.92")
    plugins.set(
        listOf(
            "com.jetbrains.php:212.4746.100",
            "yaml",
            "org.jetbrains.plugins.go:212.4746.92",
            "Docker",
            "pythonid:212.4746.96",
            "org.jetbrains.plugins.ruby:212.4746.92",
            "Kotlin",
            "coverage",
            "CSS",
            "java-i18n",
            "properties",
            "java"
        )
    )
    pluginName.set(".env files support")
}

tasks {
    patchPluginXml {
        sinceBuild.set("212")
        untilBuild.set("213.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runPluginVerifier {
        ideVersions.set(listOf("IU-213.3714.440"))
    }
}