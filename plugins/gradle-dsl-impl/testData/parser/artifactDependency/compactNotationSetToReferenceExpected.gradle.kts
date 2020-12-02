val version by extra("3.6")
val name by extra("guava")

dependencies {
  testCompile("org.gradle.test.classifiers:service:${version}")
  testCompile("com.google.guava:${name}:+")
}
