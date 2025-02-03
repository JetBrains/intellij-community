plugins {
    alias(libs.plugins.kotlinJvm)
}

tasks.register("prepareResources") {
    val outputDir = layout.buildDirectory.dir("shared-resources")
    outputs.dir(outputDir)

    val parentProjectName = rootProject.name
    val currentProjectName = project.name
    val currentProjectVersion = "1.0.0"

    doLast {
        println("Preparing shared resources in the 'common' module...")

        val jsonConfig = """
            {
              "parentProject": "$parentProjectName",
              "currentProject": {
                "name": "$currentProjectName",
                "version": "$currentProjectVersion",
                "description": "shared resources"
              }
            }
        """.trimIndent()

        val configFile = outputDir.get().asFile.resolve("config.json")
        configFile.parentFile.mkdirs()
        configFile.writeText(jsonConfig)

        println("Shared resources prepared at: ${configFile.absolutePath}")
    }
}




