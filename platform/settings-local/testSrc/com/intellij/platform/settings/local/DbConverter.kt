// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.util.SystemProperties
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.MVStoreTool
import org.h2.mvstore.type.ByteArrayDataType
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.StreamWriteFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.cbor.CBORMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import java.nio.file.Files
import java.nio.file.Path

object DumpDevIdeaCacheDb {
  @JvmStatic
  fun main(args: Array<String>) {
    DbConverter.main(arrayOf(SystemProperties.getUserHome() + "/projects/idea/out/dev-data/idea/config/app-internal-state.db"))
  }
}

object InspectDevIdeaCacheDb {
  @JvmStatic
  fun main(args: Array<String>) {
    MVStoreTool.dump(SystemProperties.getUserHome() + "/projects/idea/out/dev-data/idea/config/app-internal-state.db", true)
  }
}

// CBOR-encoded values to YAML
// TOML/JSON is not so readable as YAML for large nested data, after evaluation YAML was chosen.
object DbConverter {
  @JvmStatic
  fun main(args: Array<String>) {
    val mvMapBuilder = MVMap.Builder<String, ByteArray>().keyType(ModernStringDataType).valueType(ByteArrayDataType.INSTANCE)

    for (arg in args) {
      val store = MVStore.Builder().fileName(arg).readOnly().open()
      Files.newBufferedWriter(Path.of("$arg.yaml")).use { fileOut ->
        for (mapName in store.mapNames) {
          fileOut.write("# Map: $mapName\n")
          YAMLFactory.builder()
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .disable(YAMLWriteFeature.SPLIT_LINES)
            .enable(YAMLWriteFeature.ALLOW_LONG_KEYS)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .build()
            .createGenerator(ObjectWriteContext.empty(), fileOut).use { out ->
              val map = store.openMap(mapName, mvMapBuilder)
              val cborMapper = CBORMapper.builder().build()
              val jsonMapper = JsonMapper.builder().build()
              out.writeStartObject()
              for ((k, v) in map.entries) {
                val tree = cborMapper.readTree(v)
                out.writeName(k)
                jsonMapper.writeTree(out, tree)
              }
              out.writeEndObject()
            }
        }
      }
    }
  }
}