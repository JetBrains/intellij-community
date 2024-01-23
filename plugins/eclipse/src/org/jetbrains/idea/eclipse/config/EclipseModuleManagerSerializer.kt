// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.CustomModuleComponentSerializer
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentWriter
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl.*
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader

/**
 * Implements loading and saving configuration from [EclipseModuleManagerImpl] in iml file when workspace model is used
 */
class EclipseModuleManagerSerializer : CustomModuleComponentSerializer {
  override fun loadComponent(detachedModuleEntity: ModuleEntity.Builder,
                             reader: JpsFileContentReader,
                             imlFileUrl: VirtualFileUrl,
                             errorReporter: ErrorReporter,
                             virtualFileManager: VirtualFileUrlManager) {
    val componentTag = reader.loadComponent(imlFileUrl.url, "EclipseModuleManager") ?: return
    val entity = EclipseProjectPropertiesEntity(LinkedHashMap(), ArrayList(), ArrayList(), ArrayList(), false, 0,
                                                LinkedHashMap(), detachedModuleEntity.entitySource) {
      this.module = detachedModuleEntity
    }
    (entity as EclipseProjectPropertiesEntity.Builder).apply {
      componentTag.getChildren(LIBELEMENT).forEach {
        eclipseUrls.add(virtualFileManager.getOrCreateFromUri(it.getAttributeValue(VALUE_ATTR)!!))
      }
      componentTag.getChildren(VARELEMENT).forEach {
        variablePaths = variablePaths.toMutableMap().also { map ->
          map[it.getAttributeValue(VAR_ATTRIBUTE)!!] =
            it.getAttributeValue(PREFIX_ATTR, "") + it.getAttributeValue(VALUE_ATTR)
        }
      }
      componentTag.getChildren(CONELEMENT).forEach {
        unknownCons.add(it.getAttributeValue(VALUE_ATTR)!!)
      }
      forceConfigureJdk = componentTag.getAttributeValue(FORCED_JDK)?.toBoolean() ?: false
      val srcDescriptionTag = componentTag.getChild(SRC_DESCRIPTION)
      if (srcDescriptionTag != null) {
        expectedModuleSourcePlace = srcDescriptionTag.getAttributeValue(EXPECTED_POSITION)?.toInt() ?: 0
        srcDescriptionTag.getChildren(SRC_FOLDER).forEach {
          srcPlace = srcPlace.toMutableMap().also { map ->
            map[it.getAttributeValue(VALUE_ATTR)!!] = it.getAttributeValue(EXPECTED_POSITION)!!.toInt()
          }
        }
      }
    }
  }

  override fun saveComponent(moduleEntity: ModuleEntity, imlFileUrl: VirtualFileUrl, writer: JpsFileContentWriter) {
    val moduleOptions = moduleEntity.customImlData?.customModuleOptions
    if (moduleOptions != null && moduleOptions[JpsProjectLoader.CLASSPATH_ATTRIBUTE] == JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID) {
      return
    }
    val eclipseProperties = moduleEntity.eclipseProperties
    if (eclipseProperties == null || eclipseProperties.eclipseUrls.isEmpty() && eclipseProperties.variablePaths.isEmpty()
        && !eclipseProperties.forceConfigureJdk && eclipseProperties.unknownCons.isEmpty()) {
      return
    }

    val componentTag = JDomSerializationUtil.createComponentElement("EclipseModuleManager")
    eclipseProperties.eclipseUrls.forEach {
      componentTag.addContent(Element(LIBELEMENT).setAttribute(VALUE_ATTR, it.url))
    }
    eclipseProperties.variablePaths.forEach { (name, path) ->
      val prefix = listOf(SRC_PREFIX, SRC_LINK_PREFIX, LINK_PREFIX).firstOrNull { name.startsWith(it) } ?: ""
      val varTag = Element(VARELEMENT)
      varTag.setAttribute(VAR_ATTRIBUTE, name.removePrefix(prefix))
      if (prefix != "") {
        varTag.setAttribute(PREFIX_ATTR, prefix)
      }
      varTag.setAttribute(VALUE_ATTR, path)
      componentTag.addContent(varTag)
    }
    eclipseProperties.unknownCons.forEach {
      componentTag.addContent(Element(CONELEMENT).setAttribute(VALUE_ATTR, it))
    }
    if (eclipseProperties.forceConfigureJdk) {
      componentTag.setAttribute(FORCED_JDK, true.toString())
    }
    val srcDescriptionTag = Element(SRC_DESCRIPTION)
    srcDescriptionTag.setAttribute(EXPECTED_POSITION, eclipseProperties.expectedModuleSourcePlace.toString())
    eclipseProperties.srcPlace.forEach { (url, position) ->
      srcDescriptionTag.addContent(Element(SRC_FOLDER).setAttribute(VALUE_ATTR, url).setAttribute(EXPECTED_POSITION, position.toString()))
    }
    componentTag.addContent(srcDescriptionTag)
    writer.saveComponent(imlFileUrl.url, "EclipseModuleManager", componentTag)
  }
}