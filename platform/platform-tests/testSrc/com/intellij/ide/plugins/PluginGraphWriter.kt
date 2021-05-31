// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

internal class PluginGraphWriter(private val pluginIdToInfo: Map<String, ModuleInfo>) {
  private val nodeIdToInfo = LinkedHashMap<ModuleInfo, Int>()

  private val dependencyLinks = LinkedHashMap<Int, MutableList<Int>>()
  private val contentLinks = LinkedHashMap<Int, MutableList<Int>>()
  private var nextId = 0

  fun write(outFile: Path) {
    val stringWriter = StringWriter()
    val writer = JsonFactory().createGenerator(stringWriter)
    writer.useDefaultPrettyPrinter()
    writer.use {
      writeGraph(writer)
    }
    Files.writeString(outFile, stringWriter.buffer)
  }

  private fun writeGraph(writer: JsonGenerator) {
    writer.obj {
      writer.array("nodes") {
        val entries = pluginIdToInfo.entries.toMutableList()
        entries.sortBy { it.value.sourceModuleName }
        for (entry in entries) {
          val item = entry.value
          if (item.packageName == null && !hasContentOrDependenciesInV2Format(item.descriptor)) {
            continue
          }

          writeModuleInfo(writer, item)
        }
      }

      writer.array("links") {
        writeLinks(writer, contentLinks, isContent = true)
        writeLinks(writer, dependencyLinks, isContent = false)
      }
    }
  }

  private fun writeModuleInfo(writer: JsonGenerator, item: ModuleInfo) {
    if (nodeIdToInfo.containsKey(item)) {
      // described as part of writing some dependency
      return
    }

    val id = nextId++
    nodeIdToInfo.put(item, id)
    writer.obj {
      // for us is very important to understand dependencies between source modules, that's why on grap source module name is used
      // for plugins as node name
      writer.writeStringField("id", id.toString())
      writer.writeStringField("name", item.name ?: item.sourceModuleName)
      writer.writeStringField("package", item.packageName)
      writer.writeStringField("sourceModule", item.sourceModuleName)
      writer.writeStringField("descriptor", pathToShortString(item.descriptorFile).replace(File.separatorChar, '/'))
      item.pluginId?.let {
        writer.writeStringField("pluginId", it)
      }
      writer.writeNumberField("symbolSize", getItemNodeSize(item))
    }

    if (!item.content.isEmpty()) {
      for (child in item.content) {
        writeModuleInfo(writer, child)
      }

      for (child in item.content) {
        contentLinks.computeIfAbsent(id) { mutableListOf() }.add(nodeIdToInfo.get(child)!!)
      }
    }

    if (!item.dependencies.isEmpty()) {
      writeDependencies(item, writer, id)
    }
  }

  private fun writeDependencies(dependentInfo: ModuleInfo, writer: JsonGenerator, dependentId: Int) {
    for (ref in dependentInfo.dependencies) {
      val dep = ref.moduleInfo
      if (!nodeIdToInfo.containsKey(dep)) {
        writeModuleInfo(writer, dep)
      }
      dependencyLinks.computeIfAbsent(dependentId) { mutableListOf() }.add(nodeIdToInfo.get(dep)!!)
    }
  }
}

private fun writeLinks(writer: JsonGenerator, links: Map<Int, List<Int>>, isContent: Boolean) {
  links.forEach(BiConsumer { source, targets ->
    for (target in targets) {
      writer.obj {
        writer.writeStringField("source", source.toString())
        writer.writeStringField("target", target.toString())
        if (isContent) {
          writer.obj("lineStyle") {
            writer.writeStringField("type", "dashed")
          }
        }
        writer.array("symbol") {
          writer.writeString("none")
          writer.writeString(if (isContent) "rect" else "arrow")
        }
      }
    }
  })
}

private fun getItemNodeSize(item: ModuleInfo): Int {
  if (item.isPlugin) {
    return if (item.content.isEmpty()) 8 else 10
  }
  else {
    return 6
  }
}
