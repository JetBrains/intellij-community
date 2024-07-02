plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

//afterEvaluate {
//    val aProject = project(":a")
//    println("-----")
//    aProject.configurations.forEach { config ->
//        println(config)
//    }
//    val implementationConfig = aProject.configurations.getByName("implementation")
//    implementationConfig.dependencies.add(aProject.dependencies.create("org.apache.commons:commons-lang3:3.12.0"))
//}