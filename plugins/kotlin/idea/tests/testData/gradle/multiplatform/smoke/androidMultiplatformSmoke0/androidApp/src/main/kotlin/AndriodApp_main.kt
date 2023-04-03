//region Test configuration
// - hidden: line markers
//endregion
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AndriodApp_main : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MultiplatformAndroidJvmIosLibrary_commonMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidAndJvmMain.sayHello()
        MultiplatformAndroidJvmIosLibrary_androidMain.sayHello()

        MultiplatformJvmLibrary_commonMain.sayHello()
        MultiplatformJvmLibrary_jvmMain.sayHello()

        MultiplatformAndroidLibrary_commonMain.sayHello()
        MultiplatformAndroidLibrary_androidMain.sayHello()

        JvmLibrary_main.sayHello()
    }
}
