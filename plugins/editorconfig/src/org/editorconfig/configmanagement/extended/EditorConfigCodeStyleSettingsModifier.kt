// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.extended

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper
import com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor
import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper
import com.intellij.ide.impl.computeOnPooledThread
import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleConstraints
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import org.ec4j.core.ResourceProperties
import org.editorconfig.EditorConfigNotifier
import org.editorconfig.Utils
import org.editorconfig.configmanagement.EditorConfigNavigationActionsFactory
import org.editorconfig.language.messages.EditorConfigBundle.message
import org.editorconfig.plugincomponents.SettingsProviderComponent
import org.editorconfig.settings.EditorConfigSettings
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Consumer

private val LOG = Logger.getInstance(EditorConfigCodeStyleSettingsModifier::class.java)

class EditorConfigCodeStyleSettingsModifier : CodeStyleSettingsModifier {
  private val myReportedErrorIds: MutableSet<String> = HashSet()

  override fun modifySettings(settings: TransientCodeStyleSettings, psiFile: PsiFile): Boolean {
    val file = psiFile.virtualFile
    if (Utils.isFullIntellijSettingsSupport() && file != null &&
        (!ApplicationManager.getApplication().isUnitTestMode || Handler.isEnabledInTests())) {
      val project = psiFile.project
      if (!project.isDisposed && Utils.isEnabled(settings)) {
        // Get editorconfig settings
        try {
          return Handler.runWithTimeout(project) {
            val (properties, editorConfigs) = Handler.processEditorConfig(project, psiFile)
            // Apply editorconfig settings for the current editor
            if (Handler.applyCodeStyleSettings(settings, properties, psiFile)) {
              settings.addDependencies(editorConfigs)
              val navigationFactory = EditorConfigNavigationActionsFactory.getInstance(psiFile)
              navigationFactory?.updateEditorConfigFilePaths(editorConfigs.map { it.path })
              true
            }
            else false
          }
        }
        catch (toe: TimeoutException) {
          LOG.warn(toe)
          if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
            error(project, "timeout", message("error.timeout"), DisableEditorConfigAction(project), true)
          }
        }
        // TODO ec4j error handling
        //catch (e: EditorConfigException) {
        //  // TODO: Report an error, ignore for now
        //}
        catch (ex: Exception) {
          LOG.error(ex)
        }
      }
    }
    return false
  }

  @Synchronized
  fun error(project: Project?, id: String, message: @Nls String, fixAction: AnAction?, oneTime: Boolean) {
    if (oneTime) {
      if (myReportedErrorIds.contains(id)) {
        return
      }
      else {
        myReportedErrorIds.add(id)
      }
    }
    val notification = Notification(EditorConfigNotifier.GROUP_DISPLAY_ID, message("editorconfig"), message, NotificationType.ERROR)
    if (fixAction != null) {
      notification.addAction(
        object : AnAction(fixAction.templateText) {
          override fun actionPerformed(e: AnActionEvent) {
            fixAction.actionPerformed(e)
            myReportedErrorIds.remove(id)
            notification.expire()
          }
        }
      )
    }
    Notifications.Bus.notify(notification, project)
  }

  private class DisableEditorConfigAction(private val myProject: Project) : AnAction(message("action.disable")) {
    override fun actionPerformed(e: AnActionEvent) {
      CodeStyle.getSettings(myProject).getCustomSettings(EditorConfigSettings::class.java).apply {
        ENABLED = false
      }
      CodeStyleSettingsManager.getInstance(myProject).notifyCodeStyleSettingsChanged()
    }
  }

  override fun getStatusBarUiContributor(transientSettings: TransientCodeStyleSettings): CodeStyleStatusBarUIContributor {
    return EditorConfigCodeStyleStatusBarUIContributor()
  }

  override fun mayOverrideSettingsOf(project: Project): Boolean {
    return Utils.isEnabled(CodeStyle.getSettings(project)) && Utils.editorConfigExists(project)
  }

  override fun getName(): String {
    return message("editorconfig")
  }

  override fun getDisablingFunction(project: Project): Consumer<CodeStyleSettings> {
    return Consumer { settings: CodeStyleSettings ->
      val editorConfigSettings = settings.getCustomSettings(
        EditorConfigSettings::class.java)
      editorConfigSettings.ENABLED = false
    }
  }

  object Handler { // not a companion object to load less bytecode simultaneously with EditorConfigCodeStyleSettingsModifier
    private val TIMEOUT = Duration.ofSeconds(10)

    private var ourEnabledInTestOnly = false

    @JvmStatic
    internal fun isEnabledInTests() = ourEnabledInTestOnly

    @JvmStatic
    @TestOnly
    fun setEnabledInTests(isEnabledInTests: Boolean) {
      ourEnabledInTestOnly = isEnabledInTests
    }

    @Throws(TimeoutException::class)
    internal fun runWithTimeout(project: Project,
                               callable: Callable<Boolean>): Boolean {
      @Suppress("DEPRECATION_ERROR")
      val future = project.computeOnPooledThread(callable)
      try {
        return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
      }
      catch (e: InterruptedException) {
        LOG.warn(e)
      }
      // TODO error handling with ec4j
      //catch (e: ExecutionException) {
      //  if (e.cause is EditorConfigException) {
      //    throw (e.cause as EditorConfigException?)!!
      //  }
      //  LOG.error(e)
      //}
      return false
    }

    internal fun applyCodeStyleSettings(settings: TransientCodeStyleSettings,
                                       properties: ResourceProperties,
                                       file: PsiFile): Boolean {
      val processed: MutableSet<String> = HashSet()
      var isModified = false
      for (mapper in getMappers(settings, properties, file.language)) {
        processed.clear()
        isModified = isModified or processOptions(properties, settings, file.fileType, mapper, false, processed)
        isModified = isModified or processOptions(properties, settings, file.fileType, mapper, true, processed)
      }
      return isModified
    }

    private fun processOptions(properties: ResourceProperties,
                               settings: CodeStyleSettings,
                               fileType: FileType,
                               mapper: AbstractCodeStylePropertyMapper,
                               languageSpecific: Boolean,
                               processed: MutableSet<String>): Boolean {
      val langPrefix = if (languageSpecific) mapper.languageDomainId + "_" else null
      var isModified = false
      for (prop in properties.properties.values) {
        val optionKey = prop.name
        val intellijName = EditorConfigIntellijNameUtil.toIntellijName(optionKey)
        val accessor = findAccessor(mapper, intellijName, langPrefix)
        if (accessor != null) {
          val `val` = preprocessValue(accessor, properties, settings, fileType, optionKey, prop.sourceValue)
          for (dependency in getDependentProperties(optionKey, langPrefix)) {
            if (!processed.contains(dependency)) {
              val dependencyAccessor = findAccessor(mapper, dependency, null)
              if (dependencyAccessor != null) {
                isModified = isModified or dependencyAccessor.setFromString(`val`)
              }
            }
          }
          isModified = isModified or accessor.setFromString(`val`)
          processed.add(intellijName)
        }
      }
      return isModified
    }

    private fun getDependentProperties(property: String, langPrefix: String?): List<String> {
      var stripped = property.removePrefix(EditorConfigIntellijNameUtil.IDE_PREFIX)
      if (langPrefix != null) stripped = stripped.removePrefix(langPrefix)
      return when (stripped) {
        "indent_size" -> listOf("continuation_indent_size")
        else -> emptyList()
      }
    }

    private fun preprocessValue(accessor: CodeStylePropertyAccessor<*>,
                                properties: ResourceProperties,
                                settings: CodeStyleSettings,
                                fileType: FileType,
                                optionKey: String,
                                rawValue: String): String {
      val optionValue = rawValue.trim()
      if ("indent_size" == optionKey) {
        val explicitTabSize = getExplicitTabSize(properties)
        if ("tab" == optionValue) {
          return explicitTabSize ?: getDefaultTabSize(settings, fileType)
        }
        else if (isTabIndent(properties) && explicitTabSize != null) {
          return explicitTabSize
        }
      }
      else if ("max_line_length" == optionKey) {
        if (optionValue == "off") {
          return CodeStyleConstraints.MAX_RIGHT_MARGIN.toString()
        }
      }
      else if (EditorConfigValueUtil.EMPTY_LIST_VALUE == optionValue &&
               CodeStylePropertiesUtil.isAccessorAllowingEmptyList(accessor)) {
        return ""
      }
      return optionValue
    }

    private fun findAccessor(mapper: AbstractCodeStylePropertyMapper,
                             propertyName: String,
                             langPrefix: String?): CodeStylePropertyAccessor<*>? {
      if (langPrefix != null) {
        if (propertyName.startsWith(langPrefix)) {
          val prefixlessName = Strings.trimStart(propertyName, langPrefix)
          val propertyKind = IntellijPropertyKindMap.getPropertyKind(prefixlessName)
          if (propertyKind == EditorConfigPropertyKind.LANGUAGE || propertyKind == EditorConfigPropertyKind.COMMON ||
              EditorConfigIntellijNameUtil.isIndentProperty(prefixlessName)) {
            return mapper.getAccessor(prefixlessName)
          }
        }
      }
      else {
        return mapper.getAccessor(propertyName)
      }
      return null
    }

    internal fun processEditorConfig(project: Project, psiFile: PsiFile): Pair<ResourceProperties, List<VirtualFile>> {
      try {
        val file = psiFile.virtualFile
        val filePath = Utils.getFilePath(project, file)
        if (filePath != null) {
          return SettingsProviderComponent.getInstance(project).getPropertiesAndEditorConfigs(file)
        }
        else {
          if (VfsUtilCore.isBrokenLink(file)) {
            LOG.warn(file.presentableUrl + " is a broken link")
          }
        }
      }
      catch (e: Exception) { // TODO exceptions when parsing
        // Parsing exceptions may occur with incomplete files which is a normal case when .editorconfig is being edited.
        // Thus, the error is logged only when debug mode is enabled.
        LOG.debug(e)
      }
      return Pair(ResourceProperties.Builder().build(), emptyList())
    }

    private fun getExplicitTabSize(properties: ResourceProperties): String? =
      properties.properties["tab_width"]?.sourceValue

    private fun getDefaultTabSize(settings: CodeStyleSettings, fileType: FileType): String =
      settings.getIndentOptions(fileType).TAB_SIZE.toString()

    private fun isTabIndent(properties: ResourceProperties): Boolean =
      properties.properties["indent_style"].let { prop ->
        prop != null && prop.sourceValue == "tab"
      }

    private fun getMappers(settings: TransientCodeStyleSettings,
                           properties: ResourceProperties,
                           fileBaseLanguage: Language): Collection<AbstractCodeStylePropertyMapper> {
      return buildSet {
        getLanguageCodeStyleProviders(properties, fileBaseLanguage)
          .mapTo(this) { provider -> provider.getPropertyMapper(settings) }
        add(GeneralCodeStylePropertyMapper(settings))
      }
    }

    private fun getLanguageCodeStyleProviders(properties: ResourceProperties,
                                              fileBaseLanguage: Language): Collection<LanguageCodeStyleSettingsProvider> {
      val providers: MutableSet<LanguageCodeStyleSettingsProvider> = HashSet()
      val mainProvider = LanguageCodeStyleSettingsProvider.findUsingBaseLanguage(fileBaseLanguage)
      if (mainProvider != null) {
        providers.add(mainProvider)
      }
      for (langId in getLanguageIds(properties)) {
        if (langId != "any") {
          val additionalProvider = LanguageCodeStyleSettingsProvider.findByExternalLanguageId(langId)
          if (additionalProvider != null) {
            providers.add(additionalProvider)
          }
        }
        else {
          providers.clear()
          providers.addAll(LanguageCodeStyleSettingsProvider.getAllProviders())
          break
        }
      }
      return providers
    }

    private fun getLanguageIds(properties: ResourceProperties): Collection<String> {
      val langIds: MutableSet<String> = HashSet()
      for (key in properties.properties.keys) {
        if (EditorConfigIntellijNameUtil.isIndentProperty(key)) {
          langIds.add("any")
        }
        val langId = EditorConfigIntellijNameUtil.extractLanguageDomainId(key)
        if (langId != null) {
          langIds.add(langId)
        }
      }
      return langIds
    }
  }
}