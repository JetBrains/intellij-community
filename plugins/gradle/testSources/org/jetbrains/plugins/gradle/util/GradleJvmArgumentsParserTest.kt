// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArgument.OptionNotation
import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArgument.ValueNotation
import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArguments
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption.LongNotation
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption.PropertyNotation
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption.ShortNotation
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption.VarargNotation
import org.junit.jupiter.api.Test

class GradleJvmArgumentsParserTest : GradleJvmArgumentsParserTestCase() {

  @Test
  fun `test parse empty arguments`() {
    GradleJvmArguments.parse("")
      .assertNoTokens()
      .assertNoArguments()
      .assertText("")
  }

  @Test
  fun `test parse -X arguments`() {
    GradleJvmArguments.parse("-Xms256m")
      .assertTokens("-Xms256m")
      .assertArguments(OptionNotation(ShortNotation("-Xms", "256m")))
      .assertText("-Xms256m")
    GradleJvmArguments.parse("-Xmx512m")
      .assertTokens("-Xmx512m")
      .assertArguments(OptionNotation(ShortNotation("-Xmx", "512m")))
      .assertText("-Xmx512m")
    GradleJvmArguments.parse("-Xms256m -Xmx512m")
      .assertTokens("-Xms256m", "-Xmx512m")
      .assertArguments(OptionNotation(ShortNotation("-Xms", "256m")),
                       OptionNotation(ShortNotation("-Xmx", "512m")))
      .assertText("-Xms256m -Xmx512m")
  }

  @Test
  fun `test parse -D arguments`() {
    GradleJvmArguments.parse("-Dname=value")
      .assertTokens("-Dname=value")
      .assertArguments(OptionNotation(PropertyNotation("-D", "name", "value")))
      .assertText("-Dname=value")
    GradleJvmArguments.parse("-Dname=")
      .assertTokens("-Dname=")
      .assertArguments(OptionNotation(PropertyNotation("-D", "name", "")))
      .assertText("-Dname=")
  }

  @Test
  fun `test parse --add-opens arguments`() {
    GradleJvmArguments.parse("--add-opens=java.base/java.util=ALL-UNNAMED")
      .assertTokens("--add-opens=java.base/java.util=ALL-UNNAMED")
      .assertArguments(OptionNotation(LongNotation("--add-opens", "java.base/java.util=ALL-UNNAMED")))
    GradleJvmArguments.parse("--add-opens java.base/java.util=ALL-UNNAMED")
      .assertTokens("--add-opens", "java.base/java.util=ALL-UNNAMED")
      .assertArguments(OptionNotation(VarargNotation("--add-opens", "java.base/java.util=ALL-UNNAMED")))
  }

  @Test
  fun `test parse undefined arguments`() {
    GradleJvmArguments.parse("asd-asd")
      .assertTokens("asd-asd")
      .assertArguments(ValueNotation("asd-asd"))
      .assertText("asd-asd")
    GradleJvmArguments.parse("asd-asd asd-asd-asd")
      .assertTokens("asd-asd", "asd-asd-asd")
      .assertArguments(ValueNotation("asd-asd"),
                       ValueNotation("asd-asd-asd"))
      .assertText("asd-asd asd-asd-asd")
  }

  @Test
  fun `test parse -XX (undefined) arguments`() {
    GradleJvmArguments.parse("-XX:MaxMetaspaceSize=384m")
      .assertTokens("-XX:MaxMetaspaceSize=384m")
      .assertArguments(ValueNotation("-XX:MaxMetaspaceSize=384m"))
      .assertText("-XX:MaxMetaspaceSize=384m")
    GradleJvmArguments.parse("-XX:+HeapDumpOnOutOfMemoryError")
      .assertTokens("-XX:+HeapDumpOnOutOfMemoryError")
      .assertArguments(ValueNotation("-XX:+HeapDumpOnOutOfMemoryError"))
      .assertText("-XX:+HeapDumpOnOutOfMemoryError")
    GradleJvmArguments.parse("-XX:MaxMetaspaceSize=384m -XX:+HeapDumpOnOutOfMemoryError")
      .assertTokens("-XX:MaxMetaspaceSize=384m", "-XX:+HeapDumpOnOutOfMemoryError")
      .assertArguments(ValueNotation("-XX:MaxMetaspaceSize=384m"),
                       ValueNotation("-XX:+HeapDumpOnOutOfMemoryError"))
      .assertText("-XX:MaxMetaspaceSize=384m -XX:+HeapDumpOnOutOfMemoryError")
  }

  @Test
  fun `test merge -X arguments`() {
    (GradleJvmArguments.parse("-Xms256m") + GradleJvmArguments.parse("-Xms512m"))
      .assertTokens("-Xms512m")
      .assertArguments(OptionNotation(ShortNotation("-Xms", "512m")))
      .assertText("-Xms512m")
    (GradleJvmArguments.parse("-Xmx512m") + GradleJvmArguments.parse("-Xmx1g"))
      .assertTokens("-Xmx1g")
      .assertArguments(OptionNotation(ShortNotation("-Xmx", "1g")))
      .assertText("-Xmx1g")
    (GradleJvmArguments.parse("-Xms256m -Xmx512m") + GradleJvmArguments.parse("-Xms512m -Xmx1g"))
      .assertTokens("-Xms512m", "-Xmx1g")
      .assertArguments(OptionNotation(ShortNotation("-Xms", "512m")),
                       OptionNotation(ShortNotation("-Xmx", "1g")))
      .assertText("-Xms512m -Xmx1g")
  }

  @Test
  fun `test merge -D arguments`() {
    (GradleJvmArguments.parse("-Dname=value1") + GradleJvmArguments.parse("-Dname=value2"))
      .assertTokens("-Dname=value2")
      .assertArguments(OptionNotation(PropertyNotation("-D", "name", "value2")))
      .assertText("-Dname=value2")
    (GradleJvmArguments.parse("-Dname1=value") + GradleJvmArguments.parse("-Dname2=value"))
      .assertTokens("-Dname1=value", "-Dname2=value")
      .assertArguments(OptionNotation(PropertyNotation("-D", "name1", "value")),
                       OptionNotation(PropertyNotation("-D", "name2", "value")))
      .assertText("-Dname1=value -Dname2=value")
  }

  @Test
  fun `test merge undefined arguments`() {
    (GradleJvmArguments.parse("asd-asd") + GradleJvmArguments.parse("asd-asd"))
      .assertTokens("asd-asd")
      .assertArguments(ValueNotation("asd-asd"))
      .assertText("asd-asd")
    (GradleJvmArguments.parse("asd-asd-1") + GradleJvmArguments.parse("asd-asd-2"))
      .assertTokens("asd-asd-1", "asd-asd-2")
      .assertArguments(ValueNotation("asd-asd-1"),
                       ValueNotation("asd-asd-2"))
      .assertText("asd-asd-1 asd-asd-2")
  }
}