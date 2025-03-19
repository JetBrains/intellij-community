package com.intellij.completion.ml.personalization

import com.intellij.completion.ml.personalization.impl.*
import com.intellij.testFramework.UsefulTestCase
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit

class DecayingCountTest : UsefulTestCase() {
  private companion object {
    const val TEST_NAME = "test"

    val TODAY = DateUtil.today()

    fun Day.timestamp(): Double {
      return with(Calendar.getInstance()) {
        set(year, month, dayOfMonth, 18, 0)
        toInstant().epochSecond.toDouble()
      }
    }

    fun Day.update(count: Int): Day {
      return with(Calendar.getInstance()) {
        set(year, month - 1, dayOfMonth)
        add(Calendar.DATE, count)
        DateUtil.byDate(time)
      }
    }

    private fun Duration.inSeconds() = toDouble(DurationUnit.SECONDS)
  }

  fun `test decaying factor incrementation`() {
    val decayDuration = 1.hours
    fun DailyAggregatedDoubleFactor.get(timestamp: Double) = get(TEST_NAME, decayDuration, TODAY, timestamp)
    fun MutableDoubleFactor.increment(timestamp: Double) = increment(TEST_NAME, decayDuration, timestamp)
    val timestamp = TODAY.timestamp()

    val factor = UserFactorStorageBase.DailyAggregateFactor()
    assertTrue(factor.get(timestamp) == null)
    factor.increment(timestamp)
    assertTrue(factor.get(timestamp) == 1.0)
    assertTrue(factor.get(timestamp + 1.hours.inSeconds()) == 0.5)
    assertTrue(factor.get(timestamp + 2.hours.inSeconds()) == 0.25)
    factor.increment(timestamp + 1.hours.inSeconds())
    assertTrue(factor.get(timestamp + 2.hours.inSeconds()) == 0.75)
  }

  fun `test decaying factor aggregation`() {
    val decayDuration = 1.days
    fun DailyAggregatedDoubleFactor.aggregateDecayingCount(timestamp: Double) = aggregateDecayingCount(TEST_NAME, decayDuration, timestamp)

    val factor = UserFactorStorageBase.DailyAggregateFactor()

    val decayingCountName = decayingCountName(TEST_NAME, 1.days)
    val lastTimeName = lastTimeName(TEST_NAME)

    val theDayBefore = TODAY.update(-1)
    val twoDaysBefore = TODAY.update(-2)
    val threeDaysBefore = TODAY.update(-3)

    factor.setOnDate(threeDaysBefore, decayingCountName, 80.0)
    factor.setOnDate(threeDaysBefore, lastTimeName, threeDaysBefore.timestamp())
    assertTrue(factor.aggregateDecayingCount(threeDaysBefore.timestamp()) == 80.0)

    factor.setOnDate(twoDaysBefore, decayingCountName, 120.0)
    factor.setOnDate(twoDaysBefore, lastTimeName, twoDaysBefore.timestamp())
    assertTrue(factor.aggregateDecayingCount(twoDaysBefore.timestamp()) == 40 + 120.0)

    factor.setOnDate(theDayBefore, decayingCountName, 80.0)
    factor.setOnDate(theDayBefore, lastTimeName, theDayBefore.timestamp())
    assertTrue(factor.aggregateDecayingCount(theDayBefore.timestamp()) == 20 + 60 + 80.0)

    factor.setOnDate(TODAY, decayingCountName, 50.0)
    factor.setOnDate(TODAY, lastTimeName, TODAY.timestamp())
    assertTrue(factor.aggregateDecayingCount(TODAY.timestamp()) == 10 + 30 + 40 + 50.0)
  }
}