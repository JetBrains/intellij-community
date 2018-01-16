/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.actions

import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleRenamingHistoryState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPlainText
import com.intellij.psi.search.*
import com.intellij.usageView.UsageInfo
import com.intellij.usages.*
import com.intellij.util.Processor
import com.intellij.util.loadElement
import com.intellij.util.xmlb.XmlSerializationException
import com.intellij.util.xmlb.XmlSerializer
import org.jetbrains.idea.devkit.util.PsiUtil
import java.io.File
import kotlin.experimental.or

private val LOG = Logger.getInstance(MigrateModuleNamesInSourcesAction::class.java)

/**
 * This is a temporary action to be used for migrating occurrences of module names in IntelliJ IDEA sources after massive module renaming.
 */
class MigrateModuleNamesInSourcesAction : AnAction("Find/Update Module Names in Sources...", "Find and migrate to the new scheme occurrences of module names in IntelliJ IDEA project sources", null) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val moduleNames = ModuleManager.getInstance(project).modules.map { it.name }
    val targets = moduleNames.map(::ModuleNameUsageTarget).toTypedArray()
    val viewPresentation = UsageViewPresentation().apply {
      tabText = "Occurrences of Module Names"
      toolwindowTitle = "Occurrences of Module Names"
      usagesString = "occurrences of module names"
      usagesWord = "occurrence"
      codeUsagesString = "Found Occurrences"
      isOpenInNewTab = true
      isCodeUsages = false
      isUsageTypeFilteringAvailable = true
    }
    val processPresentation = FindUsagesProcessPresentation(viewPresentation).apply {
      isShowNotFoundMessage = true
      isShowPanelIfOnlyOneUsage = true
    }
    val searcherFactory = Factory<UsageSearcher> {
      val processed = HashSet<Pair<VirtualFile, Int>>()
      UsageSearcher { consumer ->
        val usageInfoConsumer = Processor<UsageInfo> {
          if (processed.add(Pair(it.virtualFile!!, it.navigationRange!!.startOffset))) {
            consumer.process(UsageInfo2UsageAdapter(it))
          }
          else true
        }
        processOccurrences(project, moduleNames, usageInfoConsumer)
      }
    }

    val renamingScheme = try {
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(VfsUtil.virtualToIoFile(project.baseDir), "module-renaming-scheme.xml"))?.let {
        XmlSerializer.deserialize(loadElement(it.inputStream), ModuleRenamingHistoryState::class.java).oldToNewName
      }
    }
    catch (e: XmlSerializationException) {
      LOG.error(e)
      return
    }

    val listener = object : UsageViewManager.UsageViewStateListener {
      override fun usageViewCreated(usageView: UsageView) {
        if (renamingScheme == null) return
        val migrateOccurrences = Runnable {
          @Suppress("UNCHECKED_CAST")
          val usages = (usageView.usages - usageView.excludedUsages) as Set<UsageInfo2UsageAdapter>
          usages.groupBy { it.file }.forEach { (file, usages) ->
            try {
              usages.sortedByDescending { it.usageInfo.navigationRange!!.startOffset }.forEach {
                var range = it.usageInfo.navigationRange!!
                if (it.document.charsSequence[range.startOffset] in listOf('"', '\'')) range = TextRange(range.startOffset + 1, range.endOffset - 1)
                val oldName = it.document.charsSequence.subSequence(range.startOffset, range.endOffset).toString()
                if (oldName !in renamingScheme) throw RuntimeException("Unknown module $oldName")
                val newName = renamingScheme[oldName]!!
                runWriteAction {
                  it.document.replaceString(range.startOffset, range.endOffset, newName)
                }
              }
            }
            catch (e: Exception) {
              throw RuntimeException("Cannot replace usage in ${file.presentableUrl}: ${e.message}", e)
            }
          }
        }
        usageView.addPerformOperationAction(migrateOccurrences, "Migrate Module Name Occurrences", "Cannot migrate occurrences", "Migrate Module Name Occurrences")
      }

      override fun findingUsagesFinished(usageView: UsageView?) {
      }
    }
    UsageViewManager.getInstance(project).searchAndShowUsages(targets, searcherFactory, processPresentation, viewPresentation, listener)
  }

  private fun processOccurrences(project: Project,
                                 moduleNames: List<String>,
                                 consumer: Processor<UsageInfo>) {
    val progress = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    progress.text = "Searching for module names..."
    val scope = GlobalSearchScope.projectScope(project)
    val searchHelper = PsiSearchHelper.SERVICE.getInstance(project)

    fun Char.isModuleNamePart() = this.isJavaIdentifierPart() || this == '-'
    fun GlobalSearchScope.filter(filter: (VirtualFile) -> Boolean) = object: DelegatingGlobalSearchScope(this) {
      override fun contains(file: VirtualFile): Boolean {
        return filter(file) && super.contains(file)
      }
    }
    fun mayMentionModuleNames(text: String) = (text.contains("JpsProject") || text.contains("package com.intellij.testGuiFramework")
                                              || text.contains("getJarPathForClass")) && !text.contains("StandardLicenseUrls")
    fun VirtualFile.isBuildScript() = when (extension) {
      "gant" -> true
      "groovy" -> VfsUtil.loadText(this).contains("package org.jetbrains.intellij.build")
      "java" -> mayMentionModuleNames(VfsUtil.loadText(this))
      "kt" -> mayMentionModuleNames(VfsUtil.loadText(this))
      else -> false
    }

    fun processCodeUsages(moduleName: String, quotedString: String, groovyOnly: Boolean) {
      val ignoredMethods = listOf("getPluginHomePath(", "getPluginHome(", "getPluginHomePathRelative(")
      val quotedOccurrencesProcessor = TextOccurenceProcessor { element, offset ->
        if (element.text != quotedString) return@TextOccurenceProcessor true
        if (ignoredMethods.any {
          element.textRange.startOffset > it.length && element.containingFile.text.startsWith(it, element.textRange.startOffset - it.length)
        }) {
          return@TextOccurenceProcessor true
        }
        consumer.process(UsageInfo(element, offset, offset + quotedString.length))
      }
      val literalsScope = if (moduleName in regularWordsUsedAsModuleNames + moduleNamesUsedAsIDs) scope.filter { it.isBuildScript() &&
                                                                                                                 !groovyOnly || it.extension in listOf("groovy", "gant") }
      else scope
      searchHelper.processElementsWithWord(quotedOccurrencesProcessor, literalsScope, quotedString,
                                           UsageSearchContext.IN_CODE or UsageSearchContext.IN_STRINGS, true)
    }

    for ((i, moduleName) in moduleNames.withIndex()) {
      progress.fraction = i.toDouble() / moduleNames.size
      progress.text2 = "Searching for \"$moduleName\""
      processCodeUsages(moduleName, "\"$moduleName\"", groovyOnly = false)
      processCodeUsages(moduleName, "'$moduleName'", groovyOnly = true)

      val plainOccurrencesProcessor = TextOccurenceProcessor { element, offset ->
        val endOffset = offset + moduleName.length
        if ((offset == 0 || element.text[offset - 1].isWhitespace()) && (endOffset == element.textLength || element.text[endOffset].isWhitespace())
          && element is PsiPlainText) {
          consumer.process(UsageInfo(element, offset, endOffset))
        }
        else true
      }
      val plainTextScope = if (moduleName in regularWordsUsedAsModuleNames + moduleNamesUsedAsIDs) scope.filter {it.name == "plugin-list.txt"} else scope
      searchHelper.processElementsWithWord(plainOccurrencesProcessor, plainTextScope, moduleName, UsageSearchContext.IN_PLAIN_TEXT, true)

      if (moduleName !in regularWordsUsedAsModuleNames) {
        val commentsOccurrencesProcessor = TextOccurenceProcessor { element, offset ->
          val endOffset = offset + moduleName.length
          if ((offset == 0 || !element.text[offset - 1].isModuleNamePart() && element.text[offset-1] != '.')
              && (endOffset == element.textLength || !element.text[endOffset].isModuleNamePart() && element.text[endOffset] != '/'
                  && !(endOffset < element.textLength - 2 && element.text[endOffset] == '.' && element.text[endOffset+1].isLetter()))
              && element is PsiComment) {
            consumer.process(UsageInfo(element, offset, endOffset))
          }
          else true
        }
        searchHelper.processElementsWithWord(commentsOccurrencesProcessor, scope, moduleName, UsageSearchContext.IN_COMMENTS, true)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = PsiUtil.isIdeaProject(e.project)
  }
}

private class ModuleNameUsageTarget(val moduleName: String) : UsageTarget, ItemPresentation {
  override fun getFiles() = null

  override fun getPresentation() = this

  override fun canNavigate() = false

  override fun getName() = "\"$moduleName\""

  override fun findUsages() {
    throw UnsupportedOperationException()
  }

  override fun canNavigateToSource() = false

  override fun isReadOnly() = true

  override fun navigate(requestFocus: Boolean) {
    throw UnsupportedOperationException()
  }

  override fun findUsagesInEditor(editor: FileEditor) {
  }

  override fun highlightUsages(file: PsiFile, editor: Editor, clearHighlights: Boolean) {
  }

  override fun isValid() = true

  override fun update() {
  }

  override fun getPresentableText() = "Occurrences of \"$moduleName\""

  override fun getLocationString() = null

  override fun getIcon(unused: Boolean) = AllIcons.Nodes.Module!!
}

private val regularWordsUsedAsModuleNames = setOf(
  "CloudBees", "AngularJS", "CloudFoundry", "CSS", "CFML", "Docker", "Dart", "EJS", "Guice", "Heroku", "Jade", "Kubernetes", "LiveEdit",
  "OpenShift", "Meteor", "NodeJS", "Perforce", "TFS", "WSL", "android", "ant", "annotations", "appcode", "asp", "aspectj", "behat", "boot", "bootstrap", "build", "blade",
  "commandLineTool", "chronon", "codeception", "common", "commander", "copyright", "coverage", "dependencies", "designer", "ddmlib", "doxygen", "draw9patch", "drupal", "duplicates", "drools", "eclipse", "el", "emma", "editorconfig",
  "extensions", "flex", "gherkin", "freemarker", "github", "gradle", "haml", "graph", "icons", "idea", "images", "ipnb", "jira", "joomla", "jbpm",
  "json", "junit", "layoutlib", "less", "localization", "manifest", "main", "markdown", "maven", "ognl", "openapi", "ninepatch", "perflib", "observable", "phing", "php", "phpspec",
  "pixelprobe", "play", "profilers", "properties", "puppet", "postcss", "python", "quirksmode", "repository", "resources", "rs", "relaxng", "restClient", "rest", "ruby", "sass", "sdklib", "seam", "ssh",
  "spellchecker", "stylus", "swift", "terminal", "tomcat", "textmate", "testData", "testFramework", "testng", "testRunner", "twig", "util", "updater", "vaadin", "vagrant", "vuejs", "velocity", "weblogic",
  "websocket", "wizard", "ws", "wordPress", "xml", "xpath", "yaml", "usageView", "error-prone", "spy-js", "WebStorm", "javac2", "dsm", "clion",
  "phpstorm", "WebComponents"
)

private val moduleNamesUsedAsIDs = setOf("spring-integration", "spring-aop", "spring-mvc", "spring-security", "spring-webflow", "git4idea", "dvlib", "hg4idea",
                                         "JsTestDriver", "ByteCodeViewer", "appcode-designer", "dsm", "flex",
                                         "google-app-engine", "phpstorm-workshop", "ruby-core", "ruby-slim", "spring-ws", "svn4idea", "Yeoman",
                                         "spring-batch", "spring-data", "sdk-common", "ui-designer")