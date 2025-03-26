// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings

enum class TransferableIdeId {
  DummyIde,
  VSCode,
  Cursor,
  Windsurf,
  VisualStudio,
  VisualStudioForMac
}

enum class TransferableIdeVersionId {
  Unknown,
  V2012,
  V2015,
  V2013,
  V2017,
  V2019,
  V2022
}

enum class TransferableLafId {
  Light,
  Dark,
  HighContrast
}

enum class TransferableKeymapId {
  Default,
  VsCode,
  VsCodeMac,
  VsForMac,
  VisualStudio2022
}

enum class TransferableIdeFeatureId {
  AiAssistant,
  CSharp,
  ChineseLanguage,
  Dart,
  DatabaseSupport,
  Debugger,
  Docker,
  DotNetDecompiler,
  DummyBuiltInFeature,
  DummyPlugin,
  EditorConfig,
  Flutter,
  Git,
  Gradle,
  IdeaVim,
  Ideolog,
  JapaneseLanguage,
  Java,
  KoreanLanguage,
  Kotlin,
  Kubernetes,
  LanguageSupport,
  LiveTemplates,
  Lombok,
  Maven,
  Monokai,
  NuGet,
  Prettier,
  ReSharper,
  RunConfigurations,
  Scala,
  Rust,
  Solarized,
  SpellChecker,
  TeamCity,
  TestExplorer,
  Toml,
  TsLint,
  Unity,
  Vue,
  WebSupport,
  Wsl,
  XamlStyler
}

object TransferableSections {
  val laf = "LAF"
  val keymap = "Keymap"
  val plugins = "Plugins"
  val recentProjects = "RecentProjects"
  val syntaxScheme = "SyntaxScheme"

  val types = listOf(laf, keymap, plugins, recentProjects, syntaxScheme)
}