// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.editorconfig.configmanagement.extended

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper
import com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor
import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper
import com.intellij.application.options.codeStyle.properties.OverrideLanguageIndentOptionsAccessor.OVERRIDE_LANGUAGE_INDENT_OPTIONS_PROPERTY_NAME
import com.intellij.editorconfig.common.EditorConfigBundle.message
import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
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
import kotlinx.coroutines.TimeoutCancellationException
import org.ec4j.core.ResourceProperties
import org.editorconfig.EditorConfigNotifier
import org.editorconfig.Utils
import org.editorconfig.configmanagement.EditorConfigNavigationActionsFactory
import org.editorconfig.configmanagement.EditorConfigUsagesCollector.logEditorConfigUsed
import org.editorconfig.plugincomponents.EditorConfigPropertiesService
import org.editorconfig.settings.EditorConfigSettings
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.function.Consumer

private val LOG: Logger
  get() = logger<EditorConfigCodeStyleSettingsModifier>()

class EditorConfigCodeStyleSettingsModifier : CodeStyleSettingsModifier {
  private val reportedErrorIds: MutableSet<String> = HashSet()

  override fun modifySettings(settings: TransientCodeStyleSettings, psiFile: PsiFile): Boolean {
    val file = psiFile.virtualFile
    if (!Utils.isFullIntellijSettingsSupport() ||
        file == null ||
        (!Handler.isEnabledInTests() && ApplicationManager.getApplication().isUnitTestMode)) {
      return false
    }

    val project = psiFile.project
    if (project.isDisposed || !Utils.isEnabled(settings)) {
      return false
    }

    return doModifySettings(psiFile, settings, project)
  }

  private fun doModifySettings(psiFile: PsiFile, settings: TransientCodeStyleSettings, project: Project): Boolean {
    try {
      // Get editorconfig settings
      val (properties, editorConfigs) = processEditorConfig(project, psiFile)
      // Apply editorconfig settings for the current editor
      if (applyCodeStyleSettings(settings, properties, psiFile)) {
        settings.addDependencies(editorConfigs)
        val navigationFactory = EditorConfigNavigationActionsFactory.getInstance(psiFile)
        navigationFactory?.updateEditorConfigFilePaths(editorConfigs.map { it.path })
        LOG.debug { "Modified for ${psiFile.name}" }
        return true
      }
      else {
        LOG.debug { "No changes for ${psiFile.name}" }
        return false
      }
    }
    catch (e: TimeoutCancellationException) {
      LOG.warn(e)
      if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
        error(project, "timeout", message("error.timeout"), DisableEditorConfigAction(project), true)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return false
  }

  @Synchronized
  fun error(project: Project?, id: String, message: @Nls String, fixAction: AnAction?, oneTime: Boolean) {
    if (oneTime) {
      if (reportedErrorIds.contains(id)) {
        return
      }
      else {
        reportedErrorIds.add(id)
      }
    }
    val notification = Notification(EditorConfigNotifier.GROUP_DISPLAY_ID, message("editorconfig"), message, NotificationType.ERROR)
    if (fixAction != null) {
      notification.addAction(
        object : AnAction(fixAction.templateText) {
          override fun actionPerformed(e: AnActionEvent) {
            fixAction.actionPerformed(e)
            reportedErrorIds.remove(id)
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

  override fun getName(): String = message("editorconfig")

  override fun getDisablingFunction(project: Project): Consumer<CodeStyleSettings> {
    return Consumer { settings: CodeStyleSettings ->
      val editorConfigSettings = settings.getCustomSettings(EditorConfigSettings::class.java)
      editorConfigSettings.ENABLED = false
    }
  }

  // not a companion object to load less bytecode simultaneously with EditorConfigCodeStyleSettingsModifier
  object Handler {
    internal fun isEnabledInTests() = ourEnabledInTestOnly

    @TestOnly
    fun setEnabledInTests(isEnabledInTests: Boolean) {
      ourEnabledInTestOnly = isEnabledInTests
    }
  }
}

private var ourEnabledInTestOnly = false

private fun processOptions(
  properties: ResourceProperties,
  settings: CodeStyleSettings,
  fileType: FileType,
  mapper: AbstractCodeStylePropertyMapper,
  languageSpecific: Boolean,
  processed: MutableSet<String>,
): Boolean {
  val langPrefix = if (languageSpecific) mapper.languageDomainId + "_" else null
  var isModified = false
  for (prop in properties.properties.values) {
    val optionKey = prop.name
    val intellijName = EditorConfigIntellijNameUtil.toIntellijName(optionKey)
    val accessor = findAccessor(mapper, intellijName, langPrefix)
    if (accessor != null) {
      val `val` = preprocessValue(accessor = accessor,
                                  properties = properties,
                                  settings = settings,
                                  fileType = fileType,
                                  optionKey = optionKey,
                                  rawValue = prop.sourceValue)
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
  if (langPrefix != null) {
    stripped = stripped.removePrefix(langPrefix)
  }
  return when (stripped) {
    "indent_size" -> listOf("continuation_indent_size", OVERRIDE_LANGUAGE_INDENT_OPTIONS_PROPERTY_NAME)
    else -> emptyList()
  }
}

private fun preprocessValue(
  accessor: CodeStylePropertyAccessor<*>,
  properties: ResourceProperties,
  settings: CodeStyleSettings,
  fileType: FileType,
  optionKey: String,
  rawValue: String,
): String {
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
  // Left for backwards compatibility
  else if (EditorConfigValueUtil.EMPTY_LIST_VALUE == optionValue &&
           CodeStylePropertiesUtil.isAccessorAllowingEmptyList(accessor)) {
    return ""
  }
  return optionValue
}

private fun findAccessor(
  mapper: AbstractCodeStylePropertyMapper,
  propertyName: String,
  langPrefix: String?,
): CodeStylePropertyAccessor<*>? {
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

private fun getExplicitTabSize(properties: ResourceProperties): String? = properties.properties.get("tab_width")?.sourceValue

private fun getDefaultTabSize(settings: CodeStyleSettings, fileType: FileType): String {
  return settings.getIndentOptions(fileType).TAB_SIZE.toString()
}

private fun isTabIndent(properties: ResourceProperties): Boolean {
  return properties.properties.get("indent_style").let { prop ->
    prop != null && prop.sourceValue == "tab"
  }
}

private fun getMappers(
  settings: TransientCodeStyleSettings,
  properties: ResourceProperties,
  fileBaseLanguage: Language,
): Collection<AbstractCodeStylePropertyMapper> {
  return buildSet {
    getLanguageCodeStyleProviders(properties, fileBaseLanguage).mapTo(this) { it.getPropertyMapper(settings) }
    add(GeneralCodeStylePropertyMapper(settings))
  }
}

private fun getLanguageCodeStyleProviders(
  properties: ResourceProperties,
  fileBaseLanguage: Language,
): Collection<LanguageCodeStyleSettingsProvider> {
  val providers = LinkedHashSet<LanguageCodeStyleSettingsProvider>()
  LanguageCodeStyleSettingsProvider.findUsingBaseLanguage(fileBaseLanguage)?.let {
    providers.add(it)
  }

  for (langId in getLanguageIds(properties)) {
    if (langId == "any") {
      providers.clear()
      providers.addAll(LanguageCodeStyleSettingsProvider.getAllProviders())
      break
    }
    else {
      LanguageCodeStyleSettingsProvider.findByExternalLanguageId(langId)?.let {
        providers.add(it)
      }
    }
  }
  return providers
}

private fun getLanguageIds(properties: ResourceProperties): Collection<String> {
  val langIds = LinkedHashSet<String>()
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

private fun applyCodeStyleSettings(settings: TransientCodeStyleSettings, properties: ResourceProperties, file: PsiFile): Boolean {
  val processed = HashSet<String>()
  var isModified = false
  for (mapper in getMappers(settings = settings, properties = properties, fileBaseLanguage = file.language)) {
    processed.clear()
    isModified = isModified or processOptions(properties = properties,
                                              settings = settings,
                                              fileType = file.fileType,
                                              mapper = mapper,
                                              languageSpecific = false,
                                              processed = processed)
    isModified = isModified or processOptions(properties = properties,
                                              settings = settings,
                                              fileType = file.fileType,
                                              mapper = mapper,
                                              languageSpecific = true,
                                              processed = processed)
  }
  if (isModified) {
    logEditorConfigUsed(file, properties)
  }
  return isModified
}

private fun processEditorConfig(project: Project, psiFile: PsiFile): Pair<ResourceProperties, List<VirtualFile>> {
  val file = psiFile.virtualFile
  val filePath = Utils.getFilePath(project, file)
  if (filePath != null) {
    return EditorConfigPropertiesService.getInstance(project).getPropertiesAndEditorConfigs(file)
  }
  else if (VfsUtilCore.isBrokenLink(file)) {
    LOG.warn("${file.presentableUrl} is a broken link")
  }
  LOG.debug { "null filepath for ${psiFile.name}" }
  return Pair(ResourceProperties.Builder().build(), emptyList())
}
