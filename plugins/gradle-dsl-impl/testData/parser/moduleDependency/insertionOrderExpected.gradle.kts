dependencies {
  api(project(mapOf("path" to ":module1")))
  implementation("androidx.constraintlayout:constraintlayout:1.1.3")
  testImplementation(project(mapOf("path" to ":module2")))
  androidTestApi(project(mapOf("path" to ":module3")))
}
