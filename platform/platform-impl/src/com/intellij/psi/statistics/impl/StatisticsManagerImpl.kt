// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.statistics.impl

import com.intellij.CommonBundle
import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager.getSystemDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.util.ScrambledInputStream
import com.intellij.util.ScrambledOutputStream
import com.intellij.util.io.outputStream
import org.jetbrains.annotations.TestOnly
import java.io.BufferedOutputStream
import java.io.IOException
import java.lang.ref.SoftReference
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.inputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class StatisticsManagerImpl : StatisticsManager(), SettingsSavingComponent {
  private val units = ArrayList(Collections.nCopies<SoftReference<StatisticsUnit>>(UNIT_COUNT, null))
  private val modifiedUnits = HashSet<StatisticsUnit>()
  private var testingStatistics = false

  private val lock = ReentrantReadWriteLock()

  override fun getUseCount(info: StatisticsInfo): Int {
    if (info === StatisticsInfo.EMPTY) {
      return 0
    }

    var useCount = 0
    for (conjunct in info.conjuncts) {
      useCount = max(doGetUseCount(conjunct), useCount)
    }
    return useCount
  }

  private fun doGetUseCount(info: StatisticsInfo): Int {
    val key = info.context
    val unitNumber = getUnitNumber(key)
    return lock.read {
      getUnit(unitNumber).getData(key, info.value)
    }
  }

  override fun getLastUseRecency(info: StatisticsInfo): Int {
    if (info === StatisticsInfo.EMPTY) return 0

    var recency = Integer.MAX_VALUE
    for (conjunct in info.conjuncts) {
      recency = min(doGetRecency(conjunct), recency)
    }
    return recency
  }

  private fun doGetRecency(info: StatisticsInfo): Int {
    val key1 = info.context
    val unitNumber = getUnitNumber(key1)
    return lock.read {
      val unit = getUnit(unitNumber)
      unit.getRecency(key1, info.value)
    }
  }

  override fun incUseCount(info: StatisticsInfo) {
    if (info === StatisticsInfo.EMPTY) return
    if (ApplicationManager.getApplication().isUnitTestMode && !testingStatistics) {
      return
    }

    for (conjunct in info.conjuncts) {
      doIncUseCount(conjunct)
    }
  }

  private fun doIncUseCount(info: StatisticsInfo) {
    val key1 = info.context
    val unitNumber = getUnitNumber(key1)
    lock.write {
      val unit = getUnit(unitNumber)
      unit.incData(key1, info.value)
      modifiedUnits.add(unit)
    }
  }

  override fun getAllValues(context: String): Array<StatisticsInfo> {
    return lock.read { getUnit(getUnitNumber(context)).getKeys2(context) }
      .map { StatisticsInfo(context, it) }
      .toTypedArray()
  }

  override suspend fun save() {
    lock.write {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        for (unit in modifiedUnits) {
          saveUnit(unit.number)
        }
      }
      modifiedUnits.clear()
    }
  }

  private fun getUnit(unitNumber: Int): StatisticsUnit {
    var unit = units[unitNumber]?.get()
    if (unit == null) {
      unit = loadUnit(unitNumber)
      units[unitNumber] = SoftReference(unit)
    }
    return unit
  }

  private fun saveUnit(unitNumber: Int) {
    val unit = units[unitNumber]?.get() ?: return
    try {
      ScrambledOutputStream(BufferedOutputStream(getPathToUnit(unitNumber).outputStream())).use {
        out -> unit.write(out)
      }
    }
    catch (e: IOException) {
      Messages.showMessageDialog(
        IdeBundle.message("error.saving.statistics", e.localizedMessage),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      )
    }
  }

  @TestOnly
  fun enableStatistics(parentDisposable: Disposable) {
    testingStatistics = true
    Disposer.register(parentDisposable, Disposable {
      lock.write {
        units.fill(null)
      }
      testingStatistics = false
    })
  }
}

private const val UNIT_COUNT = 997

private fun loadUnit(unitNumber: Int): StatisticsUnit {
  val unit = StatisticsUnit(unitNumber)
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return unit
  }

  val path = getPathToUnit(unitNumber)
  try {
    ScrambledInputStream(path.inputStream().buffered()).use {
      unit.read(it)
    }
  }
  catch (ignored: IOException) {
  }
  catch (ignored: WrongFormatException) {
  }
  return unit
}

private fun getUnitNumber(key1: String) = abs(key1.hashCode() % UNIT_COUNT)

private fun getPathToUnit(unitNumber: Int) = storeDir.resolve("unit.$unitNumber")

private val storeDir: Path
  get() = getSystemDir().resolve("stat")
