// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.copyright

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.*
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.project.isDirectoryBased
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.serviceContainer.NonInjectable
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.actions.UpdateCopyrightProcessor
import com.maddyhome.idea.copyright.options.LanguageOptions
import com.maddyhome.idea.copyright.options.Options
import com.maddyhome.idea.copyright.util.FileTypeUtil
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

private const val DEFAULT = "default"
private const val MODULE_TO_COPYRIGHT = "module2copyright"
private const val COPYRIGHT = "copyright"
private const val ELEMENT = "element"
private const val MODULE = "module"

private val LOG = logger<CopyrightManager>()

abstract class AbstractCopyrightManager {

  internal abstract val schemeManager: SchemeManager<SchemeWrapper<CopyrightProfile>>

  protected abstract val wrapScheme: Boolean

  protected val schemeWriter = { scheme: CopyrightProfile ->
    val element = scheme.writeScheme()
    if (wrapScheme) wrapScheme(element) else element
  }

  private fun addCopyright(profile: CopyrightProfile) {
    schemeManager.addScheme(InitializedSchemeWrapper(profile, schemeWriter))
  }

  fun getCopyrights(): Collection<CopyrightProfile> = schemeManager.allSchemes.map { it.scheme }

  open fun removeCopyright(copyrightProfile: CopyrightProfile) {
    schemeManager.removeScheme(copyrightProfile.name)
  }

  fun replaceCopyright(name: String, profile: CopyrightProfile) {
    val existingScheme = schemeManager.findSchemeByName(name)
    if (existingScheme == null) {
      addCopyright(profile)
    }
    else {
      existingScheme.scheme.copyFrom(profile)
    }
  }
}

@Service(Service.Level.APP)
class IdeCopyrightManager @NonInjectable constructor(schemeManagerFactory: SchemeManagerFactory) : AbstractCopyrightManager() {
  constructor() : this(SchemeManagerFactory.getInstance())

  companion object {
    @JvmStatic
    fun getInstance() = ApplicationManager.getApplication().service<IdeCopyrightManager>()
  }

  override val schemeManager: SchemeManager<SchemeWrapper<CopyrightProfile>> =
    schemeManagerFactory.create("copyright", object : LazySchemeProcessor<SchemeWrapper<CopyrightProfile>, SchemeWrapper<CopyrightProfile>>() {
      override fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String) = fileNameWithoutExtension

      override fun createScheme(dataHolder: SchemeDataHolder<SchemeWrapper<CopyrightProfile>>,
                                name: String,
                                attributeProvider: (String) -> String?,
                                isBundled: Boolean): SchemeWrapper<CopyrightProfile> {
        return CopyrightLazySchemeWrapper(name, dataHolder, schemeWriter)
      }

    }, settingsCategory = SettingsCategory.CODE)

  init {
    schemeManager.loadSchemes()
  }

  override val wrapScheme: Boolean = true
}

@State(name = "CopyrightManager", storages = [(Storage(value = "copyright/profiles_settings.xml", exclusive = true))])
class CopyrightManager @NonInjectable constructor(private val project: Project,
                                                  schemeManagerFactory: SchemeManagerFactory,
                                                  private val ideManager: IdeCopyrightManager,
                                                  isSupportIprProjects: Boolean = true) : AbstractCopyrightManager(), PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<CopyrightManager>()
  }

  constructor(project: Project) : this(project, SchemeManagerFactory.getInstance(project), IdeCopyrightManager.getInstance())

  private var defaultCopyrightName: String? = null

  var defaultCopyright: CopyrightProfile?
    get() = defaultCopyrightName?.let { schemeManager.findSchemeByName(it)?.scheme ?: ideManager.schemeManager.findSchemeByName(it)?.scheme }
    set(value) {
      defaultCopyrightName = value?.name
    }

  val scopeToCopyright = LinkedHashMap<String, String>()
  val options = Options()

  private val schemeManagerIprProvider = if (project.isDirectoryBased || !isSupportIprProjects) null else SchemeManagerIprProvider("copyright")

  override val schemeManager: SchemeManager<SchemeWrapper<CopyrightProfile>> = schemeManagerFactory.create("copyright", object : LazySchemeProcessor<SchemeWrapper<CopyrightProfile>, SchemeWrapper<CopyrightProfile>>("myName") {
    override fun createScheme(dataHolder: SchemeDataHolder<SchemeWrapper<CopyrightProfile>>,
                              name: String,
                              attributeProvider: (String) -> String?,
                              isBundled: Boolean): SchemeWrapper<CopyrightProfile> {
      return CopyrightLazySchemeWrapper(name, dataHolder, schemeWriter)
    }

    override fun isSchemeFile(name: CharSequence): Boolean = !StringUtil.equals(name, "profiles_settings.xml")

    override fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String {
      val schemeKey = super.getSchemeKey(attributeProvider, fileNameWithoutExtension)
      if (schemeKey != null) {
        return schemeKey
      }
      LOG.warn("Name is not specified for scheme $fileNameWithoutExtension, file name will be used instead")
      return fileNameWithoutExtension
    }
  }, schemeNameToFileName = OLD_NAME_CONVERTER, streamProvider = schemeManagerIprProvider)

  override val wrapScheme: Boolean
    get() = project.isDirectoryBased

  init {
    val app = ApplicationManager.getApplication()
    if (project.isDirectoryBased || !app.isUnitTestMode) {
      schemeManager.loadSchemes()
    }
  }

  @TestOnly
  fun loadSchemes() {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode)
    schemeManager.loadSchemes()
  }

  fun mapCopyright(scopeName: String, copyrightProfileName: String) {
    scopeToCopyright.put(scopeName, copyrightProfileName)
  }

  fun unmapCopyright(scopeName: String) {
    scopeToCopyright.remove(scopeName)
  }

  fun hasAnyCopyrights(): Boolean {
    return defaultCopyrightName != null || !scopeToCopyright.isEmpty()
  }

  override fun removeCopyright(copyrightProfile: CopyrightProfile) {
    super.removeCopyright(copyrightProfile)
    val it = scopeToCopyright.keys.iterator()
    while (it.hasNext()) {
      if (scopeToCopyright.get(it.next()) == copyrightProfile.name) {
        it.remove()
      }
    }
  }

  override fun getState(): Element? {
    val result = Element("settings")
    try {
      schemeManagerIprProvider?.writeState(result)

      if (!scopeToCopyright.isEmpty()) {
        val map = Element(MODULE_TO_COPYRIGHT)
        for ((scopeName, profileName) in scopeToCopyright) {
          val e = Element(ELEMENT)
          e
            .setAttribute(MODULE, scopeName)
            .setAttribute(COPYRIGHT, profileName)
          map.addContent(e)
        }
        result.addContent(map)
      }

      options.writeExternal(result)
    }
    catch (e: WriteExternalException) {
      LOG.error(e)
      return null
    }

    defaultCopyrightName?.let {
      result.setAttribute(DEFAULT, it)
    }

    return wrapState(result, project)
  }

  override fun loadState(state: Element) {
    val data = unwrapState(state, project, schemeManagerIprProvider, schemeManager) ?: return
    data.getChild(MODULE_TO_COPYRIGHT)?.let {
      for (element in it.getChildren(ELEMENT)) {
        scopeToCopyright.put(element.getAttributeValue(MODULE), element.getAttributeValue(COPYRIGHT))
      }
    }

    try {
      defaultCopyrightName = data.getAttributeValue(DEFAULT)
      options.readExternal(data)
    }
    catch (e: InvalidDataException) {
      LOG.error(e)
    }
  }

  fun clearMappings() {
    scopeToCopyright.clear()
  }

  fun getCopyrightOptions(file: PsiFile): CopyrightProfile? {
    val virtualFile = file.virtualFile
    if (virtualFile == null || options.getOptions(virtualFile.fileType.name).getFileTypeOverride() == LanguageOptions.NO_COPYRIGHT) {
      return null
    }

    val validationManager = DependencyValidationManager.getInstance(file.project)
    for (scopeName in scopeToCopyright.keys) {
      val packageSet = validationManager.getScope(scopeName)?.value ?: continue
      if (packageSet.contains(file, validationManager)) {
        scopeToCopyright.get(scopeName)?.let { schemeManager.findSchemeByName(it) ?: ideManager.schemeManager.findSchemeByName(it)} ?.let { return it.scheme }
      }
    }
    return defaultCopyright
  }
}

private class CopyrightManagerDocumentListener : BulkFileListener {
  private val newFilePaths = ConcurrentCollectionFactory.createConcurrentSet<String>()

  private val isDocumentListenerAdded = AtomicBoolean()

  override fun after(events: List<VFileEvent>) {
    for (event in events) {
      if (event.isFromRefresh) {
        continue
      }

      if (event is VFileCreateEvent || event is VFileMoveEvent) {
        newFilePaths.add(event.path)
        if (isDocumentListenerAdded.compareAndSet(false, true)) {
          addDocumentListener()
        }
      }
    }
  }

  private fun addDocumentListener() {
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        if (newFilePaths.isEmpty()) {
          return
        }

        val virtualFile = FileDocumentManager.getInstance().getFile(e.document) ?: return
        if (!newFilePaths.remove(virtualFile.path)) {
          return
        }

        val projectManager = serviceIfCreated<ProjectManager>() ?: return
        for (project in projectManager.openProjects) {
          if (project.isDisposed) {
            continue
          }

          handleEvent(virtualFile, project)
        }
      }
    }, FileTypeUtil.getInstance())
  }

  private fun handleEvent(virtualFile: VirtualFile, project: Project) {
    val copyrightManager = CopyrightManager.getInstance(project)
    if (!copyrightManager.hasAnyCopyrights()) return

    val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(virtualFile) ?: return
    if (!FileTypeUtil.isSupportedFile(virtualFile)) {
      return
    }

    val file = PsiManager.getInstance(project).findFile(virtualFile) ?: return

    if (!file.isWritable) {
      return
    }

    copyrightManager.getCopyrightOptions(file) ?: return

    AppUIExecutor.onUiThread(ModalityState.nonModal()).later().withDocumentsCommitted(project).execute {
      if (project.isDisposed || !file.isValid) {
        return@execute
      }

      UpdateCopyrightProcessor(project, module, file).run()
    }
  }
}

private fun wrapScheme(element: Element): Element {
  val wrapper = Element("component")
      .setAttribute("name", "CopyrightManager")
  wrapper.addContent(element)
  return wrapper
}

private class CopyrightLazySchemeWrapper(name: String,
                                         dataHolder: SchemeDataHolder<SchemeWrapper<CopyrightProfile>>,
                                         writer: (scheme: CopyrightProfile) -> Element,
                                         private val subStateTagName: String = "copyright") : LazySchemeWrapper<CopyrightProfile>(name, dataHolder, writer) {
  override val lazyScheme = lazy {
    val scheme = CopyrightProfile()
    @Suppress("NAME_SHADOWING")
    val dataHolder = this.dataHolder.getAndSet(null)
    var element = dataHolder.read()
    if (element.name != subStateTagName) {
      element = element.getChild(subStateTagName)
    }

    element.deserializeInto(scheme)
    // use effective name instead of probably missed from the serialized
    // https://youtrack.jetbrains.com/v2/issue/IDEA-186546
    scheme.profileName = name

    @Suppress("DEPRECATION")
    val allowReplaceKeyword = scheme.allowReplaceKeyword
    if (allowReplaceKeyword != null && scheme.allowReplaceRegexp == null) {
      scheme.allowReplaceRegexp = StringUtil.escapeToRegexp(allowReplaceKeyword)
      @Suppress("DEPRECATION")
      scheme.allowReplaceKeyword = null
    }

    scheme.resetModificationCount()
    dataHolder.updateDigest(writer(scheme))
    scheme
  }
}