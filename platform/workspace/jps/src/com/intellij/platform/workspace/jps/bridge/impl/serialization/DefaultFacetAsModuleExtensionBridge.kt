// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.bridge.JpsModuleExtensionBridge
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.childrenFacets
import org.jdom.Element
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer

internal class DefaultFacetAsModuleExtensionBridge : JpsModuleExtensionBridge {
  override fun loadModuleExtensions(moduleEntity: ModuleEntity, jpsModule: JpsModule) {
    loadFacets(moduleEntity.facets, jpsModule, null)
  }

  private fun loadFacets(facets: List<FacetEntity>, jpsModule: JpsModule, parentFacet: JpsElement?) {
    facets.forEach { facet ->
      val serializer = JpsFacetSerializer.getModuleExtensionSerializer(facet.typeId.name) ?: return@forEach
      val configuration = facet.configurationXmlTag?.let { JDOMUtil.load(it) } ?: Element(JpsFacetSerializer.CONFIGURATION_TAG)
      val extension = serializer.loadExtension(configuration, facet.name, jpsModule, parentFacet)
      loadFacets(facet.childrenFacets, jpsModule, extension)
    }
  }
}
