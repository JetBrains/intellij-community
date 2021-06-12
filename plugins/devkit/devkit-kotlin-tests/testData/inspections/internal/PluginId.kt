package com.intellij.openapi.extensions

class PluginId {

  fun test() {
    if (this === this) {}
    if (this !== this) {}

    val first: PluginId = PluginId()
    val second: PluginId? = first

    if (this === second) {}
    if (second !== this) {}

    if (<warning descr="'PluginId' instances should be compared by 'equals()', not '=='">first === second</warning>) {}
    if (<warning descr="'PluginId' instances should be compared by 'equals()', not '=='">first !== second</warning>) {}

    if (<warning descr="'PluginId' instances should be compared by 'equals()', not '=='">second === first</warning>) {}
    if (<warning descr="'PluginId' instances should be compared by 'equals()', not '=='">second !== first</warning>) {}

    val third: MyPluginId = first

    if (<warning descr="'PluginId' instances should be compared by 'equals()', not '=='">third === first</warning>) {}
    if (<warning descr="'PluginId' instances should be compared by 'equals()', not '=='">third !== first</warning>) {}
  }
}

typealias MyPluginId = PluginId