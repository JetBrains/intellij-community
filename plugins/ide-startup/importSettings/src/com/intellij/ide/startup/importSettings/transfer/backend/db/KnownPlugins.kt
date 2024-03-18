// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.db

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.startup.importSettings.TransferableIdeFeatureId
import com.intellij.ide.startup.importSettings.models.BuiltInFeature
import com.intellij.ide.startup.importSettings.models.FeatureInfo
import com.intellij.ide.startup.importSettings.models.PluginFeature
import com.intellij.openapi.extensions.PluginId

object KnownPlugins {
  val ReSharper: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.ReSharper, "ReSharper", isHidden = true)

  val Git: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Git, "Git")
  val editorconfig: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.EditorConfig, "editorconfig")
  @Suppress("HardCodedStringLiteral")
  val WebSupport: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.WebSupport, "Web support", "HTML, CSS, JS")
  val Docker: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Docker, "Docker")

  val Java: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Java, "Java")
  val Kotlin: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Kotlin, "Kotlin")
  val CSharp: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.CSharp, "C#")
  val NuGet: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.NuGet, "NuGet")
  val TestExplorer: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.TestExplorer, "TestExplorer")
  val RunConfigurations: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.RunConfigurations, "Run Configurations")
  val Unity: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Unity, "Unity")
  val LiveTemplates: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.LiveTemplates, "Live Templates")
  val SpellChecker: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.SpellChecker, "Spell Checker")
  val LanguageSupport: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.LanguageSupport, "Language Support")
  val DotNetDecompiler: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.DotNetDecompiler, ".NET Decompiler")
  val DatabaseSupport: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.DatabaseSupport, "Database Support")
  val TSLint: FeatureInfo =
    if (PluginManager.isPluginInstalled(PluginId.getId("tslint")))
      BuiltInFeature(TransferableIdeFeatureId.TsLint, "TSLint")
    else
      PluginFeature(TransferableIdeFeatureId.TsLint, "TSLint", "TSLint")
  val Maven: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Maven, "Maven")
  val Gradle: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Gradle, "Gradle")
  val Debugger: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Debugger, "Debugger")
  val WindowsSubsystemLinux: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Wsl, "WSL")
  val Toml: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Toml, "TOML")
  val Vue: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Vue, "Vue.js")
  val AiAssistant: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.AiAssistant, "AI Assistant")
  val Rust: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Rust,"Rust Support")
  // Language packs

  val ChineseLanguage: PluginFeature = PluginFeature(TransferableIdeFeatureId.ChineseLanguage, "com.intellij.zh", "Chinese (Simplified) Language Pack / 中文语言包")
  val KoreanLanguage: PluginFeature = PluginFeature(TransferableIdeFeatureId.KoreanLanguage, "com.intellij.ko", "Korean Language Pack / 한국어 언어 팩")
  val JapaneseLanguage: PluginFeature = PluginFeature(TransferableIdeFeatureId.JapaneseLanguage, "com.intellij.ja", "Japanese Language Pack / 日本語言語パック")

  // Plugins

  val XAMLStyler: PluginFeature = PluginFeature(TransferableIdeFeatureId.XamlStyler, "xamlstyler.rider", "XAML Styler")
  val Ideolog: PluginFeature = PluginFeature(TransferableIdeFeatureId.Ideolog, "com.intellij.ideolog", "Ideolog (logging)")
  val IdeaVim: PluginFeature = PluginFeature(TransferableIdeFeatureId.IdeaVim, "IdeaVIM", "IdeaVIM")
  val TeamCity: PluginFeature = PluginFeature(TransferableIdeFeatureId.TeamCity, "Jetbrains TeamCity Plugin", "TeamCity")
  val Scala: PluginFeature = PluginFeature(TransferableIdeFeatureId.Scala, "org.intellij.scala", "Scala")
  val Dart: PluginFeature = PluginFeature(TransferableIdeFeatureId.Dart, "Dart", "Dart")
  val Flutter: PluginFeature = PluginFeature(TransferableIdeFeatureId.Flutter, "io.flutter", "Flutter")
  val Lombok: PluginFeature = PluginFeature(TransferableIdeFeatureId.Lombok, "Lombook Plugin", "Lombok")
  val Prettier: PluginFeature = PluginFeature(TransferableIdeFeatureId.Prettier, "intellij.prettierJS", "Prettier")
  val Kubernetes: PluginFeature = PluginFeature(TransferableIdeFeatureId.Kubernetes, "com.intellij.kubernetes", "Kubernetes")

  // Themes
  val Monokai: PluginFeature = PluginFeature(TransferableIdeFeatureId.Monokai, "monokai-pro", "Monokai")
  val Solarized: PluginFeature = PluginFeature(TransferableIdeFeatureId.Solarized, "com.tylerthrailkill.intellij.solarized", "Solarized")

  val DummyBuiltInFeature: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.DummyBuiltInFeature, "")
  val DummyPlugin: PluginFeature = PluginFeature(TransferableIdeFeatureId.DummyPlugin, "", "")
}
