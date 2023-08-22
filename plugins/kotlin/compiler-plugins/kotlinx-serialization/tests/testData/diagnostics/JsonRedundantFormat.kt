@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import kotlinx.serialization.*
import kotlinx.serialization.json.*

object Instance

val defaultWarn = <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json {}</warning>
val receiverWarn = <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.encodeToString(Instance)
val noWarnFormat = Json {encodeDefaults = true}
val receiverNoWarn = noWarnFormat.encodeToString(Instance)
val defaultNoWarn = Json.encodeToString(Instance)

class SomeContainerClass {
    val memberDefaultWarn = <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json {}</warning>
    val memberReceiverWarn = <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.encodeToString(Instance)
    val memberNoWarnFormat = Json {encodeDefaults = true}
    val memberReceiverNoWarn = noWarnFormat.encodeToString(Instance)
    val memberDefaultNoWarn = Json.encodeToString(Instance)

    fun testDefaultWarnings() {
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json {}</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json() {}</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json {}</warning>.encodeToString(Any())
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json {}</warning>.encodeToString(Instance)
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json { /*some comment*/ }</warning>.encodeToString(Instance)
        val localDefaultFormat = <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json {}</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json(Json.Default) {}</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json(Json) {}</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json(Json.Default, {})</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json(builderAction = {})</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json(builderAction = fun JsonBuilder.() {})</warning>
        <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json(builderAction = fun JsonBuilder.() = Unit)</warning>

        "{}".let {
            <warning descr="[JSON_FORMAT_REDUNDANT_DEFAULT] Redundant creation of Json default format. Creating instances for each usage can be slow.">Json {}</warning>.decodeFromString<Any>(it)
        }
    }

    fun testReceiverWarnings() {
        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.encodeToString(Instance)
        val encoded = <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.encodeToString(Instance)
        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.decodeFromString<Any>("{}")
        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.hashCode()
        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.toString()

        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json(noWarnFormat) {encodeDefaults = true}</warning>.encodeToString(Instance)
        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json(builderAction = {encodeDefaults = true})</warning>.encodeToString(Instance)
        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json(noWarnFormat, {encodeDefaults = true})</warning>.encodeToString(Instance)
        <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json(builderAction = fun JsonBuilder.() {encodeDefaults = true})</warning>.encodeToString(Instance)

        "{}".let {
            <warning descr="[JSON_FORMAT_REDUNDANT] Redundant creation of Json format. Creating instances for each usage can be slow.">Json {encodeDefaults = true}</warning>.decodeFromString<Any>(it)
        }
    }

    fun testReceiverNoWarnings() {
        val localFormat = Json {encodeDefaults = true}
        localFormat.encodeToString(Instance)
        localFormat.decodeFromString<Any>("{}")
        localFormat.hashCode()
        localFormat.toString()
    }

    fun testDefaultNoWarnings() {
        val localDefault = Json
        Json.encodeToString(Instance)
        Json.decodeFromString<Any>("{}")
        Json.hashCode()
        Json.toString()
        Json(builderAction = this::builder)
        Json(Json.Default, this::builder)
    }

    private fun builder(builder: JsonBuilder) {
        //now its empty builder
    }
}


