plugins {
    kotlin("jvm") version "2.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.alibaba.fastjson2:fastjson2-kotlin:2.0.63")
    implementation(kotlin("reflect"))
}
