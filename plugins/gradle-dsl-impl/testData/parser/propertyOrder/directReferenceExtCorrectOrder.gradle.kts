val prop1 by extra(10)
extra["prop1"] = 20

android {
  defaultConfig {
    minSdkVersion(prop1)
  }
}
