apply(plugin="com.android.application")
apply(plugin="cool.plug.in")
apply(plugin="rad.plug.in")
apply(plugin="awesome.plug.in")
val newProp by extra(true)
android {
  defaultConfig {
    applicationId = "google.simpleapplication"
    minSdkVersion = 27
    versionCode = 1
    versionName = "1.0"
  }
}

dependencies {
  implementation("com.android.support:appcompat-v7:27.0.2")
}
