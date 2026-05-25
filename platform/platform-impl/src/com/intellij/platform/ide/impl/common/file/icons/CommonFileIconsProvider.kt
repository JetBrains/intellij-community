// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.common.file.icons

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import javax.swing.Icon

private val iconsByExtension: Map<String, Icon> = mapOf(
  "avsc" to AllIcons.FileTypes.Json,
  "bash" to AllIcons.Nodes.Console,
  "bat" to AllIcons.FileTypes.MicrosoftWindows,
  "bazel" to AllIcons.FileTypes.Bazel,
  "c" to AllIcons.FileTypes.C,
  "cc" to AllIcons.FileTypes.Cpp,
  "cfg" to AllIcons.FileTypes.Config,
  "cjs" to AllIcons.FileTypes.JavaScript,
  "cmd" to AllIcons.FileTypes.MicrosoftWindows,
  "cnf" to AllIcons.FileTypes.Config,
  "cpp" to AllIcons.FileTypes.Cpp,
  "cs" to AllIcons.FileTypes.Csharp,
  "csh" to AllIcons.Nodes.Console,
  "css" to AllIcons.FileTypes.Css,
  "csv" to AllIcons.FileTypes.Csv,
  "dockerfile" to AllIcons.FileTypes.Docker,
  "editorconfig" to AllIcons.Nodes.Editorconfig,
  "fish" to AllIcons.Nodes.Console,
  "gitignore" to AllIcons.FileTypes.Gitignore,
  "go" to AllIcons.Language.GO,
  "gradle" to AllIcons.FileTypes.Gradle,
  "graphql" to AllIcons.FileTypes.Graphql,
  "groovy" to AllIcons.FileTypes.Groovy,
  "h" to AllIcons.FileTypes.H,
  "hcl" to AllIcons.FileTypes.Terraform,
  "htm" to AllIcons.FileTypes.Html,
  "html" to AllIcons.FileTypes.Html,
  "http" to AllIcons.FileTypes.Http,
  "iml" to AllIcons.Nodes.IdeaModule,
  "ini" to AllIcons.FileTypes.Config,
  "ipynb" to AllIcons.FileTypes.Jupyter,
  "java" to AllIcons.FileTypes.Java,
  "jenkinsfile" to AllIcons.FileTypes.Jenkins,
  "js" to AllIcons.FileTypes.JavaScript,
  "json" to AllIcons.FileTypes.Json,
  "jsonl" to AllIcons.FileTypes.Json,
  "jsp" to AllIcons.FileTypes.Jsp,
  "jsx" to AllIcons.FileTypes.JavaScript,
  "kt" to AllIcons.Language.Kotlin,
  "kts" to AllIcons.Language.Kotlin,
  "less" to AllIcons.FileTypes.Css,
  "markdown" to AllIcons.FileTypes.Markdown,
  "md" to AllIcons.FileTypes.Markdown,
  "mjs" to AllIcons.FileTypes.JavaScript,
  "mts" to AllIcons.FileTypes.JavaScript,
  "patch" to AllIcons.Vcs.Patch_file,
  "php" to AllIcons.Language.Php,
  "pl" to AllIcons.FileTypes.Perl,
  "plist" to AllIcons.FileTypes.Xml,
  "properties" to AllIcons.FileTypes.Properties,
  "proto" to AllIcons.FileTypes.Idl,
  "ps1" to AllIcons.FileTypes.MicrosoftWindows,
  "puml" to AllIcons.FileTypes.Diagram,
  "py" to AllIcons.Language.Python,
  "pyi" to AllIcons.Language.Python,
  "rb" to AllIcons.Language.Ruby,
  "rst" to AllIcons.FileTypes.Rst,
  "sbt" to AllIcons.Language.Scala,
  "scala" to AllIcons.Language.Scala,
  "scss" to AllIcons.FileTypes.Css,
  "sh" to AllIcons.Nodes.Console,
  "sql" to AllIcons.FileTypes.Sql,
  "svg" to AllIcons.FileTypes.Image,
  "swift" to AllIcons.FileTypes.SwiftLang,
  "tab" to AllIcons.FileTypes.Csv,
  "tf" to AllIcons.FileTypes.Terraform,
  "tfvars" to AllIcons.FileTypes.Terraform,
  "toml" to AllIcons.FileTypes.Toml,
  "ts" to AllIcons.FileTypes.JavaScript,
  "tsv" to AllIcons.FileTypes.Csv,
  "tsx" to AllIcons.FileTypes.JavaScript,
  "txt" to AllIcons.FileTypes.Text,
  "vue" to AllIcons.FileTypes.Vue,
  "webmanifest" to AllIcons.FileTypes.Manifest,
  "wsdl" to AllIcons.FileTypes.WsdlFile,
  "xhtml" to AllIcons.FileTypes.Xhtml,
  "xml" to AllIcons.FileTypes.Xml,
  "xsd" to AllIcons.FileTypes.XsdFile,
  "xsl" to AllIcons.FileTypes.Xml,
  "xslt" to AllIcons.FileTypes.Xml,
  "yaml" to AllIcons.FileTypes.Yaml,
  "yml" to AllIcons.FileTypes.Yaml,
)

private fun find(extension: String?): Icon? {
  return extension
    ?.lowercase(Locale.ROOT)
    ?.let(iconsByExtension::get)
}

internal class CommonFileIconsProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (file.isDirectory || file.fileType !is PlainTextLikeFileType) {
      return null
    }

    return find(file.extension ?: file.name)
  }
}