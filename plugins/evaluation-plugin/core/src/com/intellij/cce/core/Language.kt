// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core


enum class Language(val displayName: String, private val extensions: List<String>, val ideaLanguageId: String, val needSdk: Boolean = false,
                    val curlyBracket: Boolean = true) {
  JAVA("Java", listOf("java"), "JAVA", needSdk = true),
  PYTHON("Python", listOf("py"), "Python", needSdk = true, curlyBracket = false),
  KOTLIN("Kotlin", listOf("kt"), "kotlin", needSdk = true),
  RUBY("Ruby", listOf("rb"), "ruby", needSdk = true),
  SCALA("Scala", listOf("scala"), "Scala", needSdk = true),
  CPP("C++", listOf("cpp", "c", "cc"), "C++"),
  CPPRadler("Cpp", listOf("cpp", "c", "cc", "h"), "C++"),
  PHP("PHP", listOf("php"), "PHP"),
  JS("JavaScript", listOf("js", "jsx"), "JavaScript"),
  VUE("Vue", listOf("vue"), "Vue"),
  TYPESCRIPT("TypeScript", listOf("ts", "tsx"), "TypeScript"),
  GO("Go", listOf("go"), "go"),
  DART("Dart", listOf("dart"), "Dart", needSdk = true),
  RUST("Rust", listOf("rs"), "Rust"),
  CSHARP("C#", listOf("cs"), "C#"),
  CSS("CSS", listOf("css"), "CSS"),
  LESS("Less", listOf("less"), "LESS"),
  SCSS("SCSS", listOf("scss"), "SCSS"),
  SASS("SASS", listOf("sass"), "SASS"),
  HTML("HTML", listOf("html"), "HTML"),
  TERRAFORM("Terraform", listOf("tf", "tfvars"), "HCL-Terraform"),
  SQL("SQL", listOf("sql"), "SQL"),
  YAML("YAML", listOf("yaml", "yml"), "yaml"),
  JSON("JSON", listOf("json"), "JSON"),
  XML("XML", listOf("xml", "xsd", "xsl", "wsdl"), "XML"),
  MARKDOWN("Markdown", listOf("md"), "Markdown"),
  TERMINAL("TerminalOutput", listOf("terminal_output"), "TerminalOutput"),
  ANOTHER("Another", listOf(), ""),
  UNSUPPORTED("Unsupported", listOf(), ""); // TODO: There are no unsupported languages

  companion object {
    fun resolve(displayName: String): Language = entries.find { it.displayName.equals(displayName, ignoreCase = true) } ?: ANOTHER

    fun resolveByExtension(extension: String): Language = entries.find { it.extensions.contains(extension) } ?: ANOTHER
  }
}