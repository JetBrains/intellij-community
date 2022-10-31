package ml.intellij.nlc.local.utils

import com.google.gson.Gson

internal object JSON {
  val json = Gson()

  inline fun <reified T : Any> string(value: T) = json.toJson(value)

  inline fun <reified T> parse(value: String) = json.fromJson(value, T::class.java)
}
