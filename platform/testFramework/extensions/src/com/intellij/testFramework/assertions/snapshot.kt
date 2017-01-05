/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.io.StringWriter
import java.nio.file.Path
import java.util.regex.Pattern

class ListAssertEx<ELEMENT>(actual: List<ELEMENT>) : ListAssert<ELEMENT>(actual) {
  fun toMatchSnapshot(snapshotFile: Path) {
    isNotNull

    compareFileContent(actual, snapshotFile)
  }
}

private fun dumpData(data: Any): String {
  val dumperOptions = DumperOptions()
  dumperOptions.isAllowReadOnlyProperties = true
  dumperOptions.lineBreak = DumperOptions.LineBreak.UNIX
  val yaml = Yaml(DumpRepresenter(), dumperOptions)
  val writer = StringWriter()
  yaml.dump(data, writer)
  return writer.toString()
}

private class DumpRepresenter : Representer() {
  init {
    representers.put(Pattern::class.java, RepresentDump())
  }

  private inner class RepresentDump : Represent {
    override fun representData(data: Any): Node = representScalar(Tag.STR, data.toString())
  }
}

internal fun compareFileContent(actual: Any, snapshotFile: Path) {
  val expectedContent = StringUtilRt.convertLineSeparators(snapshotFile.readText())
  val actualContent = dumpData(actual)
  if (actualContent != expectedContent) {
    throw FileComparisonFailure(null, expectedContent, actualContent, snapshotFile.toString())
  }
}