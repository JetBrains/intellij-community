// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@TestApplication
internal class UsageCollectorsTest {
  @Test
  fun `application collectors skip not applicable beans and do not retry them`() {
    NotApplicableApplicationCollector.instantiations = 0
    val disposable = Disposer.newDisposable()
    try {
      maskUsageCollectors(
        UsageCollectors.APPLICATION_EP_NAME,
        listOf(
          createBean(NotApplicableApplicationCollector::class.java),
          createBean(ValidApplicationCollector::class.java),
        ),
        disposable,
      )

      assertEquals(
        listOf(ValidApplicationCollector::class.java.name),
        UsageCollectors.getApplicationCollectors(TestConsumer, false).map { it.javaClass.name },
      )
      assertEquals(1, NotApplicableApplicationCollector.instantiations)

      UsageCollectors.getApplicationCollectors(TestConsumer, false)
      assertEquals(1, NotApplicableApplicationCollector.instantiations)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `project collectors skip not applicable beans and do not retry them`() {
    NotApplicableProjectCollector.instantiations = 0
    val disposable = Disposer.newDisposable()
    try {
      maskUsageCollectors(
        UsageCollectors.PROJECT_EP_NAME,
        listOf(
          createBean(NotApplicableProjectCollector::class.java),
          createBean(ValidProjectCollector::class.java),
        ),
        disposable,
      )

      assertEquals(
        listOf(ValidProjectCollector::class.java.name),
        UsageCollectors.getProjectCollectors(TestConsumer).map { it.javaClass.name },
      )
      assertEquals(1, NotApplicableProjectCollector.instantiations)

      UsageCollectors.getProjectCollectors(TestConsumer)
      assertEquals(1, NotApplicableProjectCollector.instantiations)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `strict collector accessor fails without synthesizing not applicable exception`() {
    NotApplicableApplicationCollector.instantiations = 0
    val bean = createBean(NotApplicableApplicationCollector::class.java)

    assertThrows(IllegalStateException::class.java) { bean.collector }
    assertEquals(1, NotApplicableApplicationCollector.instantiations)

    assertThrows(IllegalStateException::class.java) { bean.collector }
    assertEquals(1, NotApplicableApplicationCollector.instantiations)
  }
}

private object TestConsumer : UsagesCollectorConsumer

private fun createBean(collectorClass: Class<out FeatureUsagesCollector>): UsageCollectorBean {
  return UsageCollectorBean().also {
    it.implementationClass = collectorClass.name
    it.setPluginDescriptor(DefaultPluginDescriptor("test"))
  }
}

private fun maskUsageCollectors(
  pointName: ExtensionPointName<UsageCollectorBean>,
  beans: List<UsageCollectorBean>,
  disposable: Disposable,
) {
  (pointName.point as ExtensionPointImpl<UsageCollectorBean>).maskAll(beans, disposable, true)
}

private class ValidApplicationCollector : ApplicationUsagesCollector() {
  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun getGroupId(): String = "fustest.application"
}

private class NotApplicableApplicationCollector : ApplicationUsagesCollector() {
  init {
    instantiations++
    throw ExtensionNotApplicableException.create()
  }

  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun getGroupId(): String = "fustest.notapplicable.application"

  companion object {
    var instantiations = 0
  }
}

private class ValidProjectCollector : ProjectUsagesCollector() {
  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun getGroupId(): String = "fustest.project"
}

private class NotApplicableProjectCollector : ProjectUsagesCollector() {
  init {
    instantiations++
    throw ExtensionNotApplicableException.create()
  }

  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun getGroupId(): String = "fustest.notapplicable.project"

  companion object {
    var instantiations = 0
  }
}
