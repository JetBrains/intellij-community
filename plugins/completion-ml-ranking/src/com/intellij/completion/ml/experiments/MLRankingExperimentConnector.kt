@file:Suppress("PublicApiImplicitType")

package com.intellij.completion.ml.experiments

import com.intellij.lang.Language

@Suppress("PublicApiImplicitType")
interface MLRankingExperimentConnector {
  fun connect(languageId: String): List<RawMLRankingExperimentData>

  companion object {
    fun getExperiments(language: Language): List<RawMLRankingExperimentData>? {
      return HardcodedMLRankingExperimentConnector::class.sealedSubclasses
        .firstOrNull { checkMatchingLanguage(language, it.objectInstance?.language) }
        ?.objectInstance?.connect(language.id)
    }

    private fun checkMatchingLanguage(language: Language, languageId: String?): Boolean {
      if (languageId == null) return false
      
      val baseLanguages = Language.getRegisteredLanguages().filter { language.isKindOf(it) }
      return baseLanguages.any { languageId.equals(it.id, ignoreCase = true) }
    }
  }
}

sealed class HardcodedMLRankingExperimentConnector(val language: String) : MLRankingExperimentConnector {
  companion object {
    object Java : HardcodedMLRankingExperimentConnector("java") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Python : HardcodedMLRankingExperimentConnector("python") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Kotlin : HardcodedMLRankingExperimentConnector("kotlin") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Scala : HardcodedMLRankingExperimentConnector("scala") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Php : HardcodedMLRankingExperimentConnector("php") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Javascript : HardcodedMLRankingExperimentConnector("javascript") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Ruby : HardcodedMLRankingExperimentConnector("ruby") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Go : HardcodedMLRankingExperimentConnector("go") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Rust : HardcodedMLRankingExperimentConnector("rust") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Swift : HardcodedMLRankingExperimentConnector("swift") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Mysql : HardcodedMLRankingExperimentConnector("mysql") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Csharp : HardcodedMLRankingExperimentConnector("C#") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object ObjectiveC : HardcodedMLRankingExperimentConnector("objectivec") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Html : HardcodedMLRankingExperimentConnector("html") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object Css : HardcodedMLRankingExperimentConnector("css") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }

    object ShellScript : HardcodedMLRankingExperimentConnector("shell script") {
      override fun connect(languageId: String) = experiments(languageId) {
        eap {
          groups = listOf(
            CommonExperiments.ControlA,
            CommonExperiments.ControlB,
          )
        }
      }
    }
  }
}
