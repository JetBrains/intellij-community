// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * A key with string name which allows fetching or computing a typed value.
 * @param <T> type of the value
 */
@ApiStatus.Obsolete
interface ValueKey<out T> {
  /**
   * @return the name of the key
   */
  val name: String

  /**
   * Starts a matching chain using this key as the selector
   * @return a matching chain object
   */
  fun match(): BeforeIf<T> {
    return ValueMatcherImpl<T, Any>(name)
  }

  /**
   * Represents a matching chain which matches a selector key against one or more "case" keys
   * @param <T> type of the result
   */
  interface BeforeIf<out T> {
    /**
     * @return the name of the key
     */
    val keyName: String

    /**
     * Continues a matching chain: the subsequent then-branch will take effect if
     * the supplied "case" key equals to the selector key
     *
     * @param key "case" key to compare with selector key
     * @param <TT> type of the "case" key
     * @return a matching chain in the then-state (then-branches are accepted afterwards)
     */
    fun <TT> ifEq(key: ValueKey<TT>): BeforeThen<out T, TT>

    /**
     * Completes the matching chain returned the matched result
     *
     * @return the result of matching, supplied by the "then"-branch which follows the "case"-key matching the selector.
     * Could be `null` if the corresponding "then"-branch produced a null value.
     * @throws java.util.NoSuchElementException if no "case"-key matched the selector.
     */
    fun get(): T

    /**
     * Completes the matching chain returned the matched result
     *
     * @return the result of matching, supplied by the "then"-branch which follows the "case"-key matching the selector.
     * Returns null if no "case"-key matched the selector, or the corresponding "then"-branch produced a null value.
     */
    fun orNull(): T?
  }

  /**
   * Represents a matching chain in the "then-state"
   * @param <T> type of the whole chain result
   * @param <TT> type of the currently matched "case"-key value
   */
  interface BeforeThen<T, TT> {
    /**
     * Adds an alternative "case"-key: the subsequent then-branch will take effect if
     * the supplied alternative "case" key equals to the selector key in addition to the previous "case"-key
     *
     * @param key an alternative "case"-key
     * @return a matching chain in the then-state (then-branches are accepted afterwards)
     */
    fun or(key: ValueKey<TT>): BeforeThen<T, TT>

    /**
     * Produces a value which will be returned as the chain result if the previous "case"-key matches the "selector"-key
     * @param value value to be returned, could be null
     * @return a matching chain in the if-state (if-branches or terminals are accepted afterwards)
     */
    fun then(value: TT?): BeforeIf<T>

    /**
     * Produces a value which will be returned as the chain result if the previous "case"-key matches the "selector"-key
     * @param fn a function to produce a value
     * @return a matching chain in the if-state (if-branches or terminals are accepted afterwards)
     */
    fun thenGet(fn: Supplier<out TT>): BeforeIf<T>
  }

  companion object {
    /**
     * Starts a matching chain using the supplied key name as the selector
     * @param name name to match
     * @return a matching chain object
     */
    @JvmStatic
    fun match(name: String): BeforeIf<Object> {
      return ValueMatcherImpl<Object, Any>(name)
    }
  }
}
