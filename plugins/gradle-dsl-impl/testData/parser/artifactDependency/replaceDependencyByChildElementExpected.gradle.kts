dependencies {
  test("org.gradle.test.classifiers:service:1.0:jdk15@jar")
  compile("com.google.guava:guava:18.0")
  testCompile("org.hibernate:hibernate:3.1") {
    isForce = true
  }
}
