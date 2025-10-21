// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module

import com.intellij.icons.AllIcons
import com.intellij.ide.starters.local.Library
import com.intellij.ide.starters.local.LibraryCategory
import com.intellij.ide.starters.shared.LibraryLink
import com.intellij.ide.starters.shared.LibraryLinkType
import org.jetbrains.idea.devkit.DevKitBundle

internal class IdePluginDependencies {
  private val PLATFORM_APIS: LibraryCategory =
    LibraryCategory("intellij-platform",
                    AllIcons.Nodes.PpLibFolder,
                    DevKitBundle.message("category.platform.title"),
                    DevKitBundle.message("category.platform.description"))

  private val PLUGINS_APIS: LibraryCategory =
    LibraryCategory("intellij-plugins",
                    AllIcons.Nodes.PpLibFolder,
                    DevKitBundle.message("category.plugins.title"),
                    DevKitBundle.message("category.plugins.description"))

  fun compose(): Library {
    return Library(
      "compose", AllIcons.FileTypes.UiForm,
      "Compose UI", DevKitBundle.message("api.compose.description"),
      "com.intellij", "intellij.platform.compose",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://www.jetbrains.com/compose-multiplatform"),
        LibraryLink(LibraryLinkType.GUIDE, "https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform.html")
      ),
      category = PLATFORM_APIS)
  }

  fun lsp(): Library {
    return Library(
      "lsp", AllIcons.Nodes.Library,
      "Language Servers (LSP)", DevKitBundle.message("api.lsp.description"),
      "com.intellij", "com.intellij.modules.lsp",
      listOf(
        LibraryLink(LibraryLinkType.REFERENCE, "https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html"),
      ),
      category = PLATFORM_APIS)
  }

  fun java(): Library {
    return Library(
      "java", AllIcons.FileTypes.Java,
      "Java", DevKitBundle.message("plugins.java.description"),
      "com.intellij", "com.intellij.java",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/27368-java")
      ),
      category = PLUGINS_APIS)
  }

  fun kotlin(): Library {
    return Library(
      "kotlin", AllIcons.Language.Kotlin,
      "Kotlin", DevKitBundle.message("plugins.kotlin.description"),
      "com.intellij", "org.jetbrains.kotlin",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/6954-kotlin")
      ),
      category = PLUGINS_APIS)
  }

  fun javascript(): Library {
    return Library(
      "javascript", AllIcons.FileTypes.JavaScript,
      "JavaScript and TypeScript", DevKitBundle.message("plugins.javascript.description"),
      "com.intellij", "JavaScript",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/22069-javascript-and-typescript")
      ),
      category = PLUGINS_APIS)
  }

  fun python(): Library {
    return Library(
      "python", AllIcons.Language.Python,
      "Python", DevKitBundle.message("plugins.python.description"),
      null, null,
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/7322-python-community-edition")
      ),
      category = PLUGINS_APIS)
  }

  fun go(): Library {
    return Library(
      "go", AllIcons.Language.GO,
      "Go", DevKitBundle.message("plugins.go.description"),
      null, null,
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/9568-go")
      ),
      category = PLUGINS_APIS)
  }

  fun php(): Library {
    return Library(
      "php", AllIcons.Language.Php,
      "PHP", DevKitBundle.message("plugins.php.description"),
      null, null,
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/6610-php")
      ),
      category = PLUGINS_APIS)
  }

  fun ruby(): Library {
    return Library(
      "ruby", AllIcons.Language.Ruby,
      "Ruby", DevKitBundle.message("plugins.ruby.description"),
      null, null,
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/1293-ruby")
      ),
      category = PLUGINS_APIS)
  }

  fun rust(): Library {
    return Library(
      "rust", AllIcons.Language.Rust,
      "Rust", DevKitBundle.message("plugins.rust.description"),
      null, null,
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/22407-rust")
      ),
      category = PLUGINS_APIS)
  }

  fun json(): Library {
    return Library(
      "json", AllIcons.FileTypes.Json,
      "JSON", DevKitBundle.message("plugins.json.description"),
      "com.intellij", "com.intellij.modules.json",
      listOf(
        LibraryLink(LibraryLinkType.REFERENCE, "https://plugins.jetbrains.com/plugin/25364-json")
      ),
      category = PLUGINS_APIS)
  }

  fun markdown(): Library {
    return Library(
      "markdown", AllIcons.FileTypes.Text,
      "Markdown", DevKitBundle.message("plugins.markdown.description"),
      "com.intellij", "org.intellij.plugins.markdown",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/7793-markdown")
      ),
      category = PLUGINS_APIS)
  }

  fun database(): Library {
    return Library(
      "database", AllIcons.Nodes.DataTables,
      "Database Tools and SQL", DevKitBundle.message("plugins.database.description"),
      "com.intellij", "com.intellij.database",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/10925-database-tools-and-sql-for-webstorm")
      ),
      category = PLUGINS_APIS)
  }

  fun properties(): Library {
    return Library(
      "properties", AllIcons.FileTypes.Properties,
      "Properties", DevKitBundle.message("plugins.properties.description"),
      "com.intellij", "com.intellij.properties",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/11594-properties")
      ),
      category = PLUGINS_APIS)
  }

  fun xml(): Library {
    return Library(
      "xml", AllIcons.FileTypes.Xml,
      "XML", DevKitBundle.message("plugins.xml.description"),
      "com.intellij", "com.intellij.modules.xml",
      emptyList(),
      category = PLUGINS_APIS)
  }

  fun yaml(): Library {
    return Library(
      "yaml", AllIcons.FileTypes.Yaml,
      "YAML", DevKitBundle.message("plugins.yaml.description"),
      "org.jetbrains", "org.jetbrains.yaml",
      listOf(
        LibraryLink(LibraryLinkType.WEBSITE, "https://plugins.jetbrains.com/plugin/13126-yaml")
      ),
      category = PLUGINS_APIS)
  }
}