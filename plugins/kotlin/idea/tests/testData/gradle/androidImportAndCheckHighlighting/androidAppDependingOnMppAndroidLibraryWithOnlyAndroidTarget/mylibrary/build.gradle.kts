plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion(28)
    defaultConfig {
        minSdkVersion(23)
        targetSdkVersion(28)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    
    buildTypes {
        getByName("release") {
            minifyEnabled(false)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_1_8)
        targetCompatibility(JavaVersion.VERSION_1_8)
    }
}

kotlin {
    android()
    
    val commonMain by sourceSets.getting
    val androidMain by sourceSets.getting
    val androidTest by sourceSets.getting
    val androidAndroidTest by sourceSets.getting
    
    commonMain.dependencies { 
        implementation(kotlin("stdlib-common"))
    }
    
    androidMain.dependencies {
        implementation("androidx.core:core-ktx:1.3.2")
        implementation("androidx.appcompat:appcompat:1.2.0")
        implementation("com.google.android.material:material:1.2.1")
    }

    androidTest.dependencies {
        implementation("junit:junit:4.+")
    }

    androidAndroidTest.dependencies {
        implementation("androidx.test.ext:junit:1.1.2")
        implementation("androidx.test.espresso:espresso-core:3.3.0")
    }
}

