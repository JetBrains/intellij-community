val namePart1 = "super"
val namePart2 by extra("awesome")
apply(from="${namePart1}${namePart2}.gradle.kts")

dependencies {
  api("${extra["group"]}:${extra["name"]}:${extra["version"]}")
}
