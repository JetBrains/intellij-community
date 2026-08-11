plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.noarg")
    kotlin("plugin.allopen")
    kotlin("plugin.lombok")
    kotlin("plugin.assignment")
    kotlin("plugin.power-assert")
}

allOpen {
    annotation("com.my.Annotation")
}