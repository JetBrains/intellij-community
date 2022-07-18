@file:Suppress("unused")

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

val context get(): Context = TODO()

actual fun myExpectation(settings: Settings): Settings {
    return SharedPreferencesSettings(context.getSharedPreferences("pref", Context.MODE_PRIVATE))
}