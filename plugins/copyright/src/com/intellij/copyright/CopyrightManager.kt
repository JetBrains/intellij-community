/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.copyright

import com.intellij.configurationStore.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.project.isDirectoryBased
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.attribute
import com.intellij.util.element
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.actions.UpdateCopyrightProcessor
import com.maddyhome.idea.copyright.options.LanguageOptions
import com.maddyhome.idea.copyright.options.Options
import com.maddyhome.idea.copyright.util.FileTypeUtil
import com.maddyhome.idea.copyright.util.NewFileTracker
import org.jdom.Element
import java.util.*
import java.util.function.Function

private const val DEFAULT = "default"
private const val MODULE_TO_COPYRIGHT = "module2copyright"
private const val COPYRIGHT = "copyright"
private const val ELEMENT = "element"
private const val MODULE = "module"

private val LOG = Logger.getInstance(CopyrightManager::class.java)

@State(name = "CopyrightManager", storages = arrayOf(Storage(value = "copyright/profiles_settings.xml", exclusive = true)))
class CopyrightManager(private val project: Project, schemeManagerFactory: SchemeManagerFactory) : PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<CopyrightManager>()
  }

  private var defaultCopyrightName: String? = null

  var defaultCopyright: CopyrightProfile?
    get() = defaultCopyrightName?.let { schemeManager.findSchemeByName(it)?.scheme }
    set(value) {
      defaultCopyrightName = value?.name
    }

  val scopeToCopyright = LinkedHashMap<String, String>()
  val options = Options()

  private val schemeWriter = { scheme: CopyrightProfile ->
    val element = scheme.writeScheme()
    if (project.isDirectoryBased) wrapScheme(element) else element
  }

  private val schemeManagerIprProvider = if (project.isDirectoryBased) null else SchemeManagerIprProvider("copyright")

  private val schemeManager = schemeManagerFactory.create("copyright", object : LazySchemeProcessor<SchemeWrapper<CopyrightProfile>, SchemeWrapper<CopyrightProfile>>("myName") {
    override fun createScheme(dataHolder: SchemeDataHolder<SchemeWrapper<CopyrightProfile>>,
                              name: String,
                              attributeProvider: Function<String, String?>,
                              isBundled: Boolean): SchemeWrapper<CopyrightProfile> {
      return CopyrightLazySchemeWrapper(name, dataHolder, schemeWriter)
    }

    override fun isSchemeFile(name: CharSequence) = !StringUtil.equals(name, "profiles_settings.xml")
  }, schemeNameToFileName = OLD_NAME_CONVERTER, streamProvider = schemeManagerIprProvider)

  init {
    val app = ApplicationManager.getApplication()
    if (project.isDirectoryBased || !app.isUnitTestMode) {
      schemeManager.loadSchemes()
    }
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

  override fun getState(): Element? {
    val result = Element("settings")
    try {
      schemeManagerIprProvider?.writeState(result)

      if (!scopeToCopyright.isEmpty()) {
        val map = Element(MODULE_TO_COPYRIGHT)
        for ((scopeName, profileName) in scopeToCopyright) {
          map.element(ELEMENT)
              .attribute(MODULE, scopeName)
              .attribute(COPYRIGHT, profileName)
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

  private fun addCopyright(profile: CopyrightProfile) {
    schemeManager.addScheme(InitializedSchemeWrapper(profile, schemeWriter))
  }

  fun getCopyrights(): Collection<CopyrightProfile> = schemeManager.allSchemes.map { it.scheme }

  fun clearMappings() {
    scopeToCopyright.clear()
  }

  fun removeCopyright(copyrightProfile: CopyrightProfile) {
    schemeManager.removeScheme(copyrightProfile.name)

    val it = scopeToCopyright.keys.iterator()
    while (it.hasNext()) {
      if (scopeToCopyright.get(it.next()) == copyrightProfile.name) {
        it.remove()
      }
    }
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

  fun getCopyrightOptions(file: PsiFile): CopyrightProfile? {
    val virtualFile = file.virtualFile
    if (virtualFile == null || options.getOptions(virtualFile.fileType.name).getFileTypeOverride() == LanguageOptions.NO_COPYRIGHT) {
      return null
    }

    val validationManager = DependencyValidationManager.getInstance(project)
    for (scopeName in scopeToCopyright.keys) {
      val packageSet = validationManager.getScope(scopeName)?.value ?: continue
      if (packageSet.contains(file, validationManager)) {
        scopeToCopyright.get(scopeName)?.let { schemeManager.findSchemeByName(it) }?.let { return it.scheme }
      }
    }
    return defaultCopyright
  }
}

private class CopyrightManagerPostStartupActivity : StartupActivity {
  val newFileTracker = NewFileTracker()

  override fun runActivity(project: Project) {
    Disposer.register(project, Disposable { newFileTracker.clear() })

    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        val virtualFile = FileDocumentManager.getInstance().getFile(e.document) ?: return
        val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(virtualFile) ?: return
        if (!newFileTracker.poll(virtualFile) ||
            !FileTypeUtil.getInstance().isSupportedFile(virtualFile) ||
            PsiManager.getInstance(project).findFile(virtualFile) == null) {
          return
        }

        ApplicationManager.getApplication().invokeLater(Runnable {
          if (!virtualFile.isValid) {
            return@Runnable
          }

          val file = PsiManager.getInstance(project).findFile(virtualFile)
          if (file != null && file.isWritable) {
            CopyrightManager.getInstance(project).getCopyrightOptions(file)?.let {
              UpdateCopyrightProcessor(project, module, file).run()
            }
          }
        }, ModalityState.NON_MODAL, project.disposed)
      }
    }, project)
  }
}

private fun wrapScheme(element: Element): Element {
  val wrapper = Element("component")
      .attribute("name", "CopyrightManager")
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