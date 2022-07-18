@file:Suppress("unused", "unused_variable")

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.util.*

fun jvmMain() {
    val settings: Settings = PropertiesSettings(Properties())
    val mySetting = settings.getInt("me", defaultValue = 281)
}
