package com.intellij.cce.core

enum class Language(val displayName: String, private val extension: String, val ideaLanguageId: String, val needSdk: Boolean = false,
                    val curlyBracket: Boolean = true) {
  JAVA("Java", "java", "JAVA", needSdk = true),
  PYTHON("Python", "py", "Python", needSdk = true, curlyBracket = false),
  KOTLIN("Kotlin", "kt", "kotlin", needSdk = true),
  RUBY("Ruby", "rb", "ruby", needSdk = true),
  SCALA("Scala", "scala", "Scala", needSdk = true),
  CPP("C++", "cpp", "ObjectiveC"),
  PHP("PHP", "php", "PHP"),
  JS("JavaScript", "js", "JavaScript"),
  TYPESCRIPT("TypeScript", "ts", "TypeScript"),
  GO("Go", "go", "go"),
  DART("Dart", "dart", "Dart", needSdk = true),
  RUST("Rust", "rs", "Rust"),
  CSHARP("C#", "cs", "C#"),
  ANOTHER("Another", "*", ""),
  UNSUPPORTED("Unsupported", "", ""); // TODO: There are no unsupported languages

  companion object {
    fun resolve(displayName: String): Language = values()
                                                   .find { it.displayName.equals(displayName, ignoreCase = true) } ?: ANOTHER

    fun resolveByExtension(extension: String): Language = values().find { it.extension == extension } ?: ANOTHER
  }
}