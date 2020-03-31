package com.intellij.configurationScript.yaml

import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.resolver.ScalarResolver

internal class LightScalarResolver : ScalarResolver {
  override fun resolve(value: String, implicit: Boolean): Tag {
    return Tag.STR
  }
}