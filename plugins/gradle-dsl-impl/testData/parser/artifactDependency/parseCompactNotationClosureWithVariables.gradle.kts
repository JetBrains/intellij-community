val appcompat by extra("com.android.support:appcompat-v7:22.1.1")
val guavaVersion by extra("18.0")

dependencies {
  compile(appcompat) {
  }
  compile("com.google.guava:guava:$guavaVersion") {
  }
}
