extra["hello"] = rootProject.extra["greetings"]

dependencies {
  compile(rootProject.extra["dep"])
}
