// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GithubIssueState
import java.text.ParseException
import java.text.SimpleDateFormat

@ApiStatus.Experimental
class GHPRSearchQuery(val terms: List<Term<*>>) {

  fun isEmpty() = terms.isEmpty()

  override fun toString(): String = terms.joinToString(" ")

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRSearchQuery) return false

    return terms == other.terms
  }

  override fun hashCode(): Int = terms.hashCode()


  companion object {
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd")
  }

  @Suppress("EnumEntryName")
  enum class QualifierName(val apiName: String) {
    `is`("is"),
    assignee("assignee"),
    author("author"),
    label("label"),
    repo("repo"),
    review("review"),
    reviewRequested("review-requested"),
    reviewedBy("reviewed-by"),
    sortBy("sort"),
    state("state") {
      override fun createTerm(value: String): Term<*> = Term.Qualifier.Enum.from<GithubIssueState>(this, value)
    },
    type("type");

    open fun createTerm(value: String): Term<*> = Term.Qualifier.Simple(this, value)

    override fun toString() = apiName
  }

  /**
   * Part of search query (search term)
   */
  sealed class Term<T : Any>(protected val value: T) {
    abstract val apiValue: String?

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Term<*>) return false

      return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    class QueryPart(value: String) : Term<String>(value) {
      override val apiValue = this.value

      override fun toString(): String = value
    }

    sealed class Qualifier<T : Any>(protected val name: QualifierName, value: T) : Term<T>(value) {
      val apiName: String = name.apiName

      override fun toString(): String = "$name:$value"

      class Simple(name: QualifierName, value: String) : Qualifier<String>(name, value) {
        override val apiValue = this.value

        private var not: Boolean = false

        fun not(): Simple {
          not = !not
          return this
        }

        override fun toString(): String {
          val minus = if (not) "-" else ""
          return "$minus$name:$value"
        }
      }

      class Enum<T : kotlin.Enum<T>>(name: QualifierName, value: T) : Qualifier<kotlin.Enum<T>>(name, value) {
        override val apiValue = this.value.name

        companion object {
          inline fun <reified T : kotlin.Enum<T>> from(name: QualifierName, value: String): Term<*> {
            return try {
              Enum(name, enumValueOf<T>(value))
            }
            catch (e: IllegalArgumentException) {
              Simple(name, value)
            }
          }
        }
      }

      sealed class Date(name: QualifierName, value: java.util.Date) : Qualifier<java.util.Date>(name, value) {
        protected fun formatDate(): String = DATE_FORMAT.format(this.value)

        override fun toString(): String = "$name:${formatDate()}"

        class Before(name: QualifierName, value: java.util.Date) : Date(name, value) {
          override val apiValue = "<${formatDate()}"

          companion object {
            fun from(name: QualifierName, value: String): Term<*> {
              val date = try {
                DATE_FORMAT.parse(value)
              }
              catch (e: ParseException) {
                return Simple(name, value)
              }
              return Before(name, date)
            }
          }
        }

        class After(name: QualifierName, value: java.util.Date) : Date(name, value) {
          override val apiValue = ">${formatDate()}"

          companion object {
            fun from(name: QualifierName, value: String): Term<*> {
              return try {
                After(name, DATE_FORMAT.parse(value))
              }
              catch (e: ParseException) {
                Simple(name, value)
              }
            }
          }
        }
      }
    }
  }
}