// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.assertions

import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.util.io.readText
import org.assertj.core.api.ListAssert
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer
import java.nio.file.Path
import java.util.regex.Pattern

class ListAssertEx<ELEMENT>(actual: List<ELEMENT>) : ListAssert<ELEMENT>(actual) {
  fun toMatchSnapshot(snapshotFile: Path) {
    isNotNull

    compareFileContent(actual, snapshotFile)
  }
}

fun dumpData(data: Any): String {
  val dumperOptions = DumperOptions()
  dumperOptions.isAllowReadOnlyProperties = true
  dumperOptions.lineBreak = DumperOptions.LineBreak.UNIX
  val yaml = Yaml(DumpRepresenter(), dumperOptions)
  return yaml.dump(data)
}

private class DumpRepresenter : Representer() {
  init {
    representers.put(Pattern::class.java, RepresentDump())
  }

  private inner class RepresentDump : Represent {
    override fun representData(data: Any): Node = representScalar(Tag.STR, data.toString())
  }
}

@Throws(FileComparisonFailure::class)
fun compareFileContent(actual: Any, snapshotFile: Path) {
  val expectedContent = StringUtilRt.convertLineSeparators(snapshotFile.readText())
  val actualContent = if (actual is String) actual else dumpData(actual)
  if (actualContent != expectedContent) {
    throw FileComparisonFailure(null, expectedContent, actualContent, snapshotFile.toString())
  }
}