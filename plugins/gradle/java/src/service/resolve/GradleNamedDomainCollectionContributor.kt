// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

class GradleNamedDomainCollectionContributor : AbstractGradleExtensionContributor() {

  override fun getParentClassName(): String = GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION

  override fun processDynamicElements(qualifierType: PsiType,
                                      clazz: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (clazz == null) return
    if (state[DELEGATED_TYPE] == true) return

    val domainObjectName = processor.getName(state) ?: return // don't complete anything because we don't know what is in container
    val processProperties = processor.shouldProcessProperties()
    val processMethods = processor.shouldProcessMethods()
    if (!processProperties && !processMethods) {
      return
    }
    var parentKey = "unknown"
    var domainObjectType = substituteTypeParameter(qualifierType, GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER, 0, false) ?: return
    if (qualifierType is GradleExtensionType && domainObjectType is PsiClassType) {
      domainObjectType = GradleExtensionType(qualifierType.path + ".*" , domainObjectType)
      parentKey = qualifierType.path
    }

    val containingFile = place.containingFile
    val manager = containingFile.manager

    if (processProperties) {
      /*
      Allows NDOC elements as properties to be valid DSL is not a great idea, because the IDE can not actually detect if the
      property in that position has already been registered, and Gradle will not register it in property mode, while it will
      in configure mode. So a setup like this:
      ```gradle
      someCollection {
         something.another.thing = "value"
      }
      ```
      is not valid, because `something` will not be automatically registered as an element in the collection, while:
      ```gradle
      someCollection {
         create("something")
         something.another.thing = "value"
      }
      ```
      is valid, because `something` will be registered as an element collection by the create-method call before the something statement.
      Compare this to the following:
      ```gradle
      someCollection {
         something {
            another.thing = "value"
         }
      }
      ```
      Which is immediately valid, because the `something` element is registered as an element in the collection by the configure-block,
      and the `another` element is registered as a property in the `something` element.

      Without further emulation of the Gradle DSL, this is not possible to implement.

      We do allow it so that navigation to the element is possible, but it will not be registered as an element in the collection, and the
      error gradle will throw without the registration might not be immediately clear to the user, if for example another extension in a scope
      outside the NDOC configure closure has already registered with the name, it will prefer to look in there, and throw a missing property
      exception.
       */
      val property = if (domainObjectType is GradleExtensionType) GradleExtensionProperty(domainObjectName, parentKey, domainObjectType, clazz) else GradleDomainObjectProperty(domainObjectName, domainObjectType, containingFile)
      val nav = domainObjectType.resolve();
      if (nav != null) {
        property.navigationElement = nav
      }

      if (!processor.execute(property, state)) {
        return
      }
    }
    if (processMethods) {
      val method = if (domainObjectType is GradleExtensionType) GradleConfigureExtensionMethod(manager, domainObjectName, parentKey, domainObjectType, clazz) else  GrLightMethodBuilder(manager, domainObjectName).apply {
        returnType = domainObjectType
        addParameter("configuration", createType(GROOVY_LANG_CLOSURE, containingFile))
        originInfo = NAMED_DOMAIN_DECLARATION

        val nav = domainObjectType.resolve();
        if (nav != null) {
          navigationElement = nav
        }
      }
      if (!processor.execute(method, state)) {
        return
      }
    }

    if (true)
      return

    if (qualifierType !is GradleExtensionType) return

    val extensionsData = GradlePropertyExtensionsContributor.getExtensionsFor(containingFile) ?: return

    val name = "${qualifierType.path}.*"
    val allExtensions = extensionsData.extensions
    val extensionKeys = allExtensions.keys.filter { it.startsWith(name) }
    val sectionCount = name.count { it == '.' } + 1 // +1 for the section after the wildcard
    val sectionNames = extensionKeys.filter { key -> key.count { it == '.'} == sectionCount && key.startsWith(name) }.toList()
    val extensions = mutableListOf<GradleExtensionsSettings.GradleExtension>();

    sectionNames.forEach { section -> allExtensions[section]?.let { extensions.add(it) } }

    if (extensions.isEmpty())
      return;

    processExtensions(extensions, containingFile, place, processor, domainObjectType.resolve()?: clazz, state)
  }

  companion object {
    const val NAMED_DOMAIN_DECLARATION = "by NamedDomainCollection"
  }
}
