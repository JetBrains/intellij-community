apply(from="../defaults.gradle.kts")

// Local overrides
val varInt by extra((rootProject.extra["numbers"] as List<*>)[1])
val varBool by extra(rootProject.extra["bool"] as Boolean)
val varRefString by extra("${(rootProject.extra["numbers"] as List<*>)[3]}}")
val varProGuardFiles by extra(mapOf("test" to "proguard-rules.txt", "debug" to debugFile))

android {
  compileSdkVersion(varInt)
  buildToolsVersion(varRefString)

  signingConfigs {
    myConfig {
      storeFile = file((extra["vars"] as Map<*,*>)["signing"]["storeF"])
      storePassword = (extra["vars"] as Map<*,*>)["signing"]["storeP"]
      keyPassword = "${(extra["vars"] as Map<*,*>)["signing"]["keyP"]}${(rootProject.extra["letters"] as List<*>)[3]}"
    }
  }

  defaultConfig {
    applicationId = rootProject.extra["appId"]
    testApplicationId = rootProject.extra["testId"]
    maxSdkVersion((extra["vars"] as Map<*,*>)["maxSdk"])
    minSdkVersion((extra["vars"] as Map<*,*>)["minSdk"])
  }
}
