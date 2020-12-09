android.defaultConfig.applicationId = "com.example.myapplication"
android.defaultConfig.setConsumerProguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
android.defaultConfig.dimension = "abcd"
android.defaultConfig.manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
android.defaultConfig.maxSdkVersion = 23
android.defaultConfig.multiDexEnabled = true
android.defaultConfig.setProguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
android.defaultConfig.testApplicationId = "com.example.myapplication.test"
android.defaultConfig.testFunctionalTest = true
android.defaultConfig.testHandleProfiling = true
android.defaultConfig.testInstrumentationRunner = "abcd"
android.defaultConfig.testInstrumentationRunnerArguments = mutableMapOf("size" to "medium", "foo" to "bar")
android.defaultConfig.useJack = true
android.defaultConfig.versionCode = 1
android.defaultConfig.versionName = "1.0"
