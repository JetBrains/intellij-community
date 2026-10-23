// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory

private const val TARGET = "//sample:sample_test"
private val SAMPLES = listOf(ShardFilterTest.SampleA::class.java, ShardFilterTest.SampleB::class.java, ShardFilterTest.SampleC::class.java,
                             ShardFilterTest.SampleD::class.java)

class ShardFilterTest {
  @Test
  fun `without a plan each test goes to a shard of its own and every test runs once`() {
    val shards = (0 until 3).map { discover(ShardFilter.create(3, it, null, TARGET)) }

    assertThat(shards.flatten()).containsExactlyInAnyOrderElementsOf(discover(null))
    assertThat(shards.flatten()).doesNotHaveDuplicates()
  }

  @Test
  fun `a plan keeps each class in one shard and balances the shards by the recorded seconds`() {
    val plan = plan(shards = 2, "SampleA" to 9.0, "SampleB" to 6.0, "SampleC" to 3.0, "SampleD" to 1.0)
    val shards = (0 until 2).map { discover(ShardFilter.create(2, it, plan, TARGET)) }

    assertThat(shards.flatten()).containsExactlyInAnyOrderElementsOf(discover(null))
    assertThat(shards.flatten()).doesNotHaveDuplicates()
    assertThat(shards.map { classesOf(it) }).containsExactly(setOf("SampleA"), setOf("SampleB", "SampleC", "SampleD"))
  }

  @Test
  fun `a class much heavier than the fair share of a shard is split by test`() {
    val plan = plan(shards = 2, "SampleA" to 100.0, "SampleB" to 1.0, "SampleC" to 1.0, "SampleD" to 1.0)
    val shards = (0 until 2).map { discover(ShardFilter.create(2, it, plan, TARGET)) }

    assertThat(shards.flatten()).containsExactlyInAnyOrderElementsOf(discover(null))
    assertThat(shards.flatten()).doesNotHaveDuplicates()
    assertThat(shards).allSatisfy { assertThat(classesOf(it)).contains("SampleA") }
    // the other classes stay whole
    for (name in listOf("SampleB", "SampleC", "SampleD")) {
      assertThat(shards.count { name in classesOf(it) }).isEqualTo(1)
    }
  }

  @Test
  fun `a nested class and a parameterized test stay with their class`() {
    val plan = plan(shards = 4, "SampleA" to 1.0, "SampleB" to 1.0, "SampleC" to 1.0, "SampleD" to 1.0)
    val shards = (0 until 4).map { discover(ShardFilter.create(4, it, plan, TARGET)) }

    assertThat(shards.flatten()).containsExactlyInAnyOrderElementsOf(discover(null))
    assertThat(shards.map { classesOf(it) }).allSatisfy { assertThat(it).hasSize(1) }
  }

  @Test
  fun `the record of a class holds the records of its nested classes`() {
    // the suite of SampleD lasts until its nested class ends, so SampleD weighs 9, not 17, and shares a shard
    val plan = plan(shards = 2, "SampleA" to 10.0, "SampleB" to 4.0, "SampleC" to 3.0, "SampleD" to 9.0, "SampleD\$Inner" to 8.0)
    val shards = (0 until 2).map { discover(ShardFilter.create(2, it, plan, TARGET)) }

    assertThat(shards.map { classesOf(it) }).containsExactly(setOf("SampleA", "SampleC"), setOf("SampleD", "SampleB"))
  }

  @Test
  fun `the records of the nested classes weigh a class without a record of its own`() {
    // SampleD weighs 9 with its nested class, so it takes a shard of its own
    val plan = plan(shards = 2, "SampleA" to 4.0, "SampleB" to 3.0, "SampleC" to 2.0, "SampleD\$Inner" to 9.0)
    val shards = (0 until 2).map { discover(ShardFilter.create(2, it, plan, TARGET)) }

    assertThat(shards.map { classesOf(it) }).containsExactly(setOf("SampleD"), setOf("SampleA", "SampleB", "SampleC"))
  }

  @Test
  fun `the plan digest changes with the lines of the target only`() {
    val plan = plan(shards = 2, "SampleA" to 1.0, "SampleB" to 2.0)
    val digest = ShardFilter.create(2, 0, plan, TARGET).planDigest

    assertThat(digest).isNotNull().isNotEqualTo(ShardFilter.NO_PLAN_DIGEST)
    assertThat(ShardFilter.create(2, 1, plan, TARGET).planDigest).isEqualTo(digest)
    // the lines of another target and their order do not count
    val other = "class\t//other:other_test\tcom.Other\t5\n"
    assertThat(ShardFilter.create(2, 0, other + plan.lines().reversed().joinToString("\n"), TARGET).planDigest).isEqualTo(digest)

    assertThat(ShardFilter.create(2, 0, plan(shards = 2, "SampleA" to 1.0, "SampleB" to 3.0), TARGET).planDigest).isNotEqualTo(digest)
    assertThat(ShardFilter.create(2, 0, plan(shards = 1, "SampleA" to 1.0, "SampleB" to 2.0), TARGET).planDigest).isNotEqualTo(digest)
    assertThat(ShardFilter.create(2, 0, "garbage", TARGET).planDigest).isEqualTo(ShardFilter.NO_PLAN_DIGEST)
    assertThat(ShardFilter.create(2, 0, null, TARGET).planDigest).isNull()
  }

  @Test
  fun `a shard at or above the planned number is idle`() {
    val plan = plan(shards = 1, "SampleA" to 1.0)

    assertThat(ShardFilter.create(4, 0, plan, TARGET).isIdle).isFalse()
    assertThat(ShardFilter.create(4, 1, plan, TARGET).isIdle).isTrue()
    assertThat(ShardFilter.create(4, 3, plan, TARGET).isIdle).isTrue()
    assertThat(discover(ShardFilter.create(4, 0, plan, TARGET))).containsExactlyInAnyOrderElementsOf(discover(null))
  }

  @Test
  fun `a target without a target line takes the default and another target's lines do not count`() {
    val other = "//other:other_test"
    val plan = "default\t3\ntarget\t$other\t1\nclass\t$other\t${SampleA::class.java.name}\t100\n"

    assertThat(ShardFilter.create(4, 2, plan, TARGET).isIdle).isFalse()
    assertThat(ShardFilter.create(4, 3, plan, TARGET).isIdle).isTrue()

    // with the 100 seconds of the other target, SampleA would be split
    val balanced = "default\t2\n" + plan(shards = 2, "SampleA" to 1.0, "SampleB" to 1.0, "SampleC" to 1.0, "SampleD" to 1.0)
      .lineSequence().filter { it.startsWith("class") }.joinToString("\n") + "\nclass\t$other\t${SampleA::class.java.name}\t100\n"
    val shards = (0 until 2).map { discover(ShardFilter.create(2, it, balanced, TARGET)) }
    assertThat(shards.count { "SampleA" in classesOf(it) }).isEqualTo(1)
  }

  @Test
  fun `a stale plan still runs every test once`() {
    // a renamed class keeps its old record, and the classes without a record weigh the mean seconds per test
    val plan = plan(shards = 3, "SampleRenamed" to 500.0, "SampleB" to 2.0)
    val shards = (0 until 3).map { discover(ShardFilter.create(3, it, plan, TARGET)) }

    assertThat(shards.flatten()).containsExactlyInAnyOrderElementsOf(discover(null))
    assertThat(shards.flatten()).doesNotHaveDuplicates()
  }

  @Test
  fun `a malformed plan leaves the hash of each test`() {
    val filter = ShardFilter.create(2, 1, "target\t$TARGET\tmany\n", TARGET)

    assertThat(filter.isIdle).isFalse()
    val shards = listOf(discover(ShardFilter.create(2, 0, "garbage", TARGET)), discover(filter))
    assertThat(shards.flatten()).containsExactlyInAnyOrderElementsOf(discover(null))
    assertThat(shards.flatten()).doesNotHaveDuplicates()
  }

  @Test
  fun `a shard that the partition leaves without tests says that it excluded tests`() {
    val plan = plan(shards = 2, "SampleE" to 1.0)
    val filter = ShardFilter.create(2, 1, plan, TARGET)

    assertThat(discover(filter, listOf(SampleE::class.java))).isEmpty()
    assertThat(filter.hasExcludedTests()).isTrue()
  }

  private fun plan(shards: Int, vararg seconds: Pair<String, Double>): String {
    return buildString {
      append("target\t").append(TARGET).append('\t').append(shards).append('\n')
      for ((name, value) in seconds) {
        append("class\t").append(TARGET).append('\t').append(ShardFilterTest::class.java.name).append('$').append(name)
          .append('\t').append(value).append('\n')
      }
    }
  }

  private fun discover(filter: ShardFilter?, classes: List<Class<*>> = SAMPLES): List<String> {
    val builder = LauncherDiscoveryRequestBuilder.request().selectors(classes.map { selectClass(it) })
    filter?.let { builder.filters(it) }
    val launcher = LauncherFactory.create(LauncherConfig.builder().enableLauncherSessionListenerAutoRegistration(false).build())
    val testPlan = launcher.discover(builder.build())
    return testPlan.roots
      .flatMap { testPlan.getDescendants(it) }
      .filter { it.isTest || it.isMethodContainer() }
      .map { it.uniqueId }
  }

  private fun TestIdentifier.isMethodContainer() = isContainer && source.orElse(null) is MethodSource

  private fun classesOf(uniqueIds: List<String>): Set<String> {
    return uniqueIds.mapTo(HashSet()) { uniqueId -> Regex("""\$(Sample[A-Z])]""").find(uniqueId)!!.groupValues[1] }
  }

  // the samples are trivial, so the run of this module runs them too

  class SampleA {
    @Test fun a1() {}
    @Test fun a2() {}
    @Test fun a3() {}
    @Test fun a4() {}
    @Test fun a5() {}
    @Test fun a6() {}
  }

  class SampleB {
    @Test fun b1() {}
    @Test fun b2() {}
  }

  class SampleC {
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3])
    fun c1(@Suppress("unused") value: Int) {}

    @Test fun c2() {}
  }

  class SampleD {
    @Test fun d1() {}

    @Nested
    inner class Inner {
      @Test fun d2() {}
    }
  }

  class SampleE {
    @Test fun e1() {}
  }
}
