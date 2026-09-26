plugins {
    kotlin("jvm")
    idea
}

val generatorTask = project.tasks.register("generator") {
    val outputDirectory = project.layout.projectDirectory.dir("src/main/kotlinGen")
    outputs.dir(outputDirectory)
    doLast {
        outputDirectory.file("generatedCommon.kt").asFile.writeText(
            //language=kotlin
            """
            fun printHello() {
                println("hello")
            }
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.getByName("main").generatedKotlin.srcDir(generatorTask)

idea {
    module {
        generatedSourceDirs.add(file("src/main/kotlinGen"))
    }
}