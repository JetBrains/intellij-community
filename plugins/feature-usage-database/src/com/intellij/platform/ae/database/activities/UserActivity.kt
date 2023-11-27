package com.intellij.platform.ae.database.activities

/**
 * Base interface for user activity – nothing but an ID.
 *
 * Any class with [UserActivity] interface should be:
 * 1. a Kotlin object
 * 2. stateless
 *
 * You don't want to implement this interface. Instead, implement:
 * * [WritableDatabaseBackedCounterUserActivity]: a counter user activity that can be stored in a database
 * * [WritableDatabaseBackedTimeSpanUserActivity]: a time span user activity that can be stored in a database
 * * [ReadableUserActivity]: if your activity can return a result
 * * [DatabaseBackedCounterUserActivity] / [DatabaseBackedTimeSpanUserActivity]: useful for readable user activities,
 * if you need to access database (it most cases you would like to do so)
 *
 * =-=-=-=-=-=-=-=-=-=-=-=-=
 *
 * Implementation example 1:
 *
 * For an upcoming campaign you need to implement a feature counter: 'The longest streak of IDE opening'.
 * To achieve the goal you need to implement the following objects:
 * 1. a [WritableDatabaseBackedTimeSpanUserActivity] that records each time IDE is started. This activity doesn't return any information, it's
 * write only. Question to careful readers: what should it return?
 * 2. a [ReadableUserActivity] that calculates the longest streak of IDE opening based on data from activity (1). This activity by itself
 * doesn't store any data
 *
 * When the developer will be asked to calculate some new value and it can be calculated with data about when IDE was running, they can easily
 * reuse data from user activity (1)
 */
sealed interface UserActivity {
  val id: String
}

/**
 * Represents an activity that can return some sort of value or several values.
 */
interface ReadableUserActivity<TResult> : UserActivity {
  /**
   * Returns a value calculated for this activity. You are free to implement other
   * 'getter' methods in your class, but this method should return some sort of
   * default value
   */
  suspend fun getActivityValue(): TResult
}