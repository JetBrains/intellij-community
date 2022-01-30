plugins {
    id("org.jetbrains.intellij") version "1.2.0"
}

group = "ru.adelf"
version = "2022.1"

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
    version.set("IU-221.3427.89-EAP-SNAPSHOT")
    plugins.set(
        listOf(
            "com.jetbrains.php:221.3427.92",
            "yaml",
            "org.jetbrains.plugins.go:221.3427.89",
            "Docker",
            "pythonid:221.3427.93",
            "org.jetbrains.plugins.ruby:221.3427.89",
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
        untilBuild.set("221.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runPluginVerifier {
        ideVersions.set(listOf("IU-213.3714.440"))
    }
}