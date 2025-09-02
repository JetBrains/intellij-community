plugins {
    `kotlin-dsl`
}

val kotlinPluginVersion: String = "{{kgp_version}}"


dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:$kotlinPluginVersion"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinPluginVersion")
}