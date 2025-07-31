// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.util.io.Base62
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.jackson.IntelliJPrettyPrinter
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import it.unimi.dsi.fastutil.bytes.ByteArrays
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap
import java.io.File
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer
import kotlin.io.path.invariantSeparatorsPathString

private class IdGenerator {
  private val collisions = Object2IntOpenCustomHashMap(ByteArrays.HASH_STRATEGY)

  fun getId(longId: String): String {
    val shortId = Base62.encode(DigestUtil.sha256().digest(longId.toByteArray())).copyOfRange(0, 2)
    return "${shortId[0].toInt().toChar()}${shortId[1].toInt().toChar()}${collisions.addTo(shortId, 1).takeIf { it != 0 } ?: ""}"
  }
}

internal class PluginGraphWriter(private val pluginIdToInfo: Map<String, ModuleInfo>, private val projectHomePath: Path) {
  private val nodeInfoToId = LinkedHashMap<ModuleInfo, String>()

  private val dependencyLinks = LinkedHashMap<String, MutableList<String>>()
  private val contentLinks = LinkedHashMap<String, MutableList<String>>()
  private val idGenerator = IdGenerator()

  fun write(outFile: Path) {
    val stringWriter = StringWriter()
    writeTo(stringWriter)
    Files.writeString(outFile, stringWriter.buffer)
  }

  fun writeTo(out: Writer, prettyPrint: Boolean = true) {
    val writer = JsonFactory().createGenerator(out)
    if (prettyPrint) {
      writer.prettyPrinter = IntelliJPrettyPrinter()
    }
    writer.use {
      writeGraph(writer)
    }
  }

  private fun writeGraph(writer: JsonGenerator) {
    writer.array {
      val entries = pluginIdToInfo.entries.toMutableList()
      entries.sortBy { it.value.sourceModule.name }
      for (entry in entries) {
        val item = entry.value
        if (item.packageName == null && !hasContentOrDependenciesInV2Format(item.descriptor)) {
          continue
        }

        writeModuleInfo(writer = writer, item = item, parentId = null)
      }

      // infer node from dependencies - after writing content to ensure that node is described if it is not in content,
      // fake modules (com.intellij.modules.*)
      for (entry in java.util.List.copyOf(nodeInfoToId.entries)) {
        if (!entry.key.dependencies.isEmpty()) {
          writeDependencies(entry.key, writer, entry.value)
        }
      }

      writeLinks(writer, contentLinks, isContent = true)
      writeLinks(writer, dependencyLinks, isContent = false)
    }
  }

  private fun writeModuleInfo(writer: JsonGenerator, item: ModuleInfo, parentId: String?) {
    assert(!nodeInfoToId.containsKey(item))

    if (isNodeSkipped(item)) {
      return
    }

    val nodeName = item.name ?: item.sourceModule.name
    val id = idGenerator.getId(nodeName)
    var compoundId: String? = null
    if (!item.content.isEmpty()) {
      compoundId = "c$id"
      writer.obj {
        writer.writeStringField("group", "nodes")
        writer.obj("data") {
          writer.writeStringField("id", compoundId)
          writer.writeStringField("n", item.pluginId!!)
        }
      }
    }

    nodeInfoToId.put(item, id)
    writer.obj {
      // for us is very important to understand dependencies between source modules, that's why on graph source module name is used
      // for plugins as node name
      writer.writeStringField("group", "nodes")
      writer.obj("data") {
        writer.writeStringField("id", id)
        writer.writeStringField("name", nodeName)
        writer.writeStringField("n", getShortName(nodeName))
        writer.writeStringField("package", item.packageName)
        writer.writeStringField("sourceModule", item.sourceModule.name)
        writer.writeStringField("descriptor", projectHomePath.relativize(item.descriptorFile).invariantSeparatorsPathString)
        item.pluginId?.let {
          writer.writeStringField("pluginId", it)
        }
        (compoundId ?: parentId)?.let {
          writer.writeStringField("parent", it)
        }
        writer.writeNumberField("type", getItemNodeType(item))
      }
    }

    if (!item.content.isEmpty()) {
      for (child in item.content) {
        writeModuleInfo(writer = writer, item = child, parentId = compoundId)
      }

      for (child in item.content) {
        contentLinks.computeIfAbsent(id) { mutableListOf() }.add(nodeInfoToId.get(child)!!)
      }
    }
  }

  private fun writeDependencies(dependentInfo: ModuleInfo, writer: JsonGenerator, dependentId: String) {
    for (ref in dependentInfo.dependencies) {
      val dep = ref.moduleInfo ?: continue
      if (isNodeSkipped(dep)) {
        continue
      }

      if (!nodeInfoToId.containsKey(dep)) {
        writeModuleInfo(writer = writer, item = dep, parentId = null)
      }
      dependencyLinks.computeIfAbsent(dependentId) { mutableListOf() }.add(nodeInfoToId.get(dep)!!)
    }
  }

  private fun getShortName(name: String): String {
    if (name.startsWith("intellij.")) {
      return "i.${name.substring("intellij.".length)}"
    }
    else if (name.startsWith("com.intellij.modules.")) {
      return "c.i.m.${name.substring("com.intellij.modules.".length)}"
    }
    else {
      return name
    }
  }
}

private fun writeLinks(writer: JsonGenerator, links: Map<String, List<String>>, isContent: Boolean) {
  links.forEach(BiConsumer { source, targets ->
    for (target in targets) {
      writer.obj {
        writer.writeStringField("group", "edges")
        writer.obj("data") {
          writer.writeStringField("id", "${source}_${target}")
          writer.writeStringField("source", source)
          writer.writeStringField("target", target)
          writer.writeNumberField("type", if (isContent) 1 else 0)
        }
      }
    }
  })
}

// skip to simplify graph
private fun isNodeSkipped(dep: ModuleInfo) = dep.name == "com.intellij.modules.ultimate" || dep.name == "com.intellij.modules.lang"

private fun getItemNodeType(item: ModuleInfo): Int {
  if (item.isPlugin) {
    return if (item.content.isEmpty()) 1 else 2
  }
  else {
    return 0
  }
}
