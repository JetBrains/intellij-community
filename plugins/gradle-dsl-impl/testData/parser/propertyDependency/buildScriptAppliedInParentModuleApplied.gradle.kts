val versions = mutableMapOf<String,String>()
versions["android_gradle_plugin"] = "3.1.0"

val deps = mutableMapOf<String,String>()
deps["android_gradle_plugin"] = "com.android.tools.build:gradle:${versions["android_gradle_plugin"]}"
extra["deps"] = deps
