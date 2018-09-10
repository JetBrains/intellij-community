// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.search.GithubIssueSearchSort
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.exceptions.GithubParseException
import java.text.ParseException
import java.text.SimpleDateFormat

class GithubPullRequestSearchQuery(private val terms: List<Term<*>>) {
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

  companion object {
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd")

    @Throws(GithubParseException::class)
    fun parseFromString(string: String): GithubPullRequestSearchQuery {
      val result = mutableListOf<Term<*>>()
      val terms = string.split(' ')
      for (term in terms) {
        if (term.isEmpty()) continue

        val colonIdx = term.indexOf(':')
        if (colonIdx < 0) {
          result.add(Term.QueryPart(term))
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
      return GithubPullRequestSearchQuery(result)
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
    after("created") {
      override fun createTerm(value: String) = Term.Qualifier.Date.After.from(this, value)
    },
    before("created") {
      override fun createTerm(value: String) = Term.Qualifier.Date.Before.from(this, value)
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

    class QueryPart(value: String) : Term<String>(value) {
      override val apiValue = this.value
    }

    sealed class Qualifier<T : Any>(name: QualifierName, value: T) : Term<T>(value) {
      val apiName: String = name.apiName

      class Simple(name: QualifierName, value: String) : Qualifier<String>(name, value) {
        override val apiValue = this.value
      }

      class Enum<T : kotlin.Enum<T>>(name: QualifierName, value: T) : Qualifier<kotlin.Enum<T>>(name, value) {
        override val apiValue = this.value.name

        companion object {
          inline fun <reified T : kotlin.Enum<T>> from(name: QualifierName, value: String): Term<*> {
            try {
              return Qualifier.Enum(name, enumValueOf<T>(value))
            }
            catch (e: IllegalArgumentException) {
              throw GithubParseException(
                "Can't parse $name from $value. Should be one of [${enumValues<T>().joinToString { it.name }}]", e)
            }
          }
        }
      }

      sealed class Date(name: QualifierName, value: java.util.Date) : Qualifier<java.util.Date>(name, value) {
        protected fun formatDate(): String = DATE_FORMAT.format(this.value)

        companion object {
          private fun getDate(name: QualifierName, value: String): java.util.Date {
            try {
              return DATE_FORMAT.parse(value)
            }
            catch (e: ParseException) {
              throw GithubParseException("Could not parse date for $name from $value. Should match the pattern ${DATE_FORMAT.toPattern()}",
                                         e)
            }
          }
        }

        class Before(name: QualifierName, value: java.util.Date) : Date(name, value) {
          override val apiValue = "<${formatDate()}"

          companion object {
            fun from(name: QualifierName, value: String): Term<*> {
              return Qualifier.Date.Before(name, Date.getDate(name, value))
            }
          }
        }

        class After(name: QualifierName, value: java.util.Date) : Date(name, value) {
          override val apiValue = ">${formatDate()}"

          companion object {
            fun from(name: QualifierName, value: String): Term<*> {
              return Qualifier.Date.After(name, Date.getDate(name, value))
            }
          }
        }
      }
    }
  }
}