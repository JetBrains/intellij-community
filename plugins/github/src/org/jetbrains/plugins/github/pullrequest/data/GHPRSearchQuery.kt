// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchSort
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import java.text.ParseException
import java.text.SimpleDateFormat

internal class GHPRSearchQuery(private val terms: List<Term<*>>) {
  fun buildApiSearchQuery(searchQueryBuilder: GithubApiSearchQueryBuilder) {
    for (term in terms) {
      when (term) {
        is Term.QueryPart -> {
          searchQueryBuilder.query(term.apiValue)
        }
        is Term.Qualifier -> {
          searchQueryBuilder.qualifier(term.apiName, term.apiValue)
        }
      }
    }
  }

  fun isEmpty() = terms.isEmpty()

  override fun toString(): String {
    val builder = StringBuilder()
    for (term in terms) {
      builder.append(term.toString())
      if (builder.isNotEmpty()) builder.append(" ")
    }
    return builder.toString()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRSearchQuery) return false

    if (terms != other.terms) return false

    return true
  }

  override fun hashCode(): Int = terms.hashCode()


  companion object {
    val DEFAULT = GHPRSearchQuery(listOf(Term.Qualifier.Enum(QualifierName.state, GithubIssueState.open)))

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd")

    fun parseFromString(string: String): GHPRSearchQuery {
      val result = mutableListOf<Term<*>>()
      val terms = string.split(' ')
      for (term in terms) {
        if (term.isEmpty()) continue

        val colonIdx = term.indexOf(':')
        if (colonIdx < 0) {
          result.add(Term.QueryPart(term.replace("#", "")))
        }
        else {
          try {
            result.add(QualifierName.valueOf(term.substring(0, colonIdx)).createTerm(term.substring(colonIdx + 1)))
          }
          catch (e: IllegalArgumentException) {
            result.add(Term.QueryPart(term))
          }
        }
      }
      return GHPRSearchQuery(result)
    }
  }

  @Suppress("EnumEntryName")
  enum class QualifierName(val apiName: String) {
    state("state") {
      override fun createTerm(value: String) = Term.Qualifier.Enum.from<GithubIssueState>(this, value)
    },
    assignee("assignee") {
      override fun createTerm(value: String) = Term.Qualifier.Simple(this, value)
    },
    author("author") {
      override fun createTerm(value: String) = Term.Qualifier.Simple(this, value)
    },
    label("label") {
      override fun createTerm(value: String) = Term.Qualifier.Simple(this, value)
    },
    after("created") {
      override fun createTerm(value: String) = Term.Qualifier.Date.After.from(this, value)
    },
    before("created") {
      override fun createTerm(value: String) = Term.Qualifier.Date.Before.from(this, value)
    },
    reviewedBy("reviewed-by") {
      override fun createTerm(value: String) = Term.Qualifier.Simple(this, value)

      override fun toString() = apiName
    },
    reviewRequested("review-requested") {
      override fun createTerm(value: String) = Term.Qualifier.Simple(this, value)

      override fun toString() = apiName
    },
    sortBy("sort") {
      override fun createTerm(value: String) = Term.Qualifier.Enum.from<GithubIssueSearchSort>(this, value)
    };

    abstract fun createTerm(value: String): Term<*>
  }

  /**
   * Part of search query (search term)
   */
  sealed class Term<T : Any>(protected val value: T) {
    abstract val apiValue: String?

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Term<*>) return false

      if (value != other.value) return false

      return true
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