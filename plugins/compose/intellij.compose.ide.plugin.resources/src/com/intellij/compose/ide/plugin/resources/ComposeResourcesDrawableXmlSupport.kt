// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.lang.ASTNode
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.xml.SchemaPrefix
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.DefaultXmlExtension
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.XmlSchemaProvider
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor
import java.util.concurrent.ConcurrentHashMap

/** Provides namespaces and their standard prefix names commonly used in Compose resources XML files. */
internal class ComposeResourcesDrawableXmlSchemaProvider : XmlSchemaProvider() {

  override fun getSchema(url: String, module: Module?, baseFile: PsiFile): XmlFile? {
    if (module == null) return null

    val originalFile = baseFile.originalFile
    if (!originalFile.isComposeResourcesFile()) return null
    if (url !in KNOWN_NAMESPACE_URIS) return null

    return getOrCreateSchemaFile(module.project, url)
  }

  override fun isAvailable(file: XmlFile): Boolean {
    val originalFile = file.originalFile as? XmlFile ?: return false
    return originalFile.isComposeResourcesFile()
  }

  override fun getAvailableNamespaces(file: XmlFile, tagName: String?): Set<String> {
    val originalFile = file.originalFile as? XmlFile ?: return emptySet()
    val resourceType = originalFile.getComposeResourceType() ?: return emptySet()
    val result = mutableSetOf(TOOLS_URI, ANDROID_URI, AUTO_URI)

    when {
      resourceType == ResourceType.DRAWABLE -> result.add(AAPT_URI)
      resourceType.isStringType -> result.add(XLIFF_URI)
    }

    return result
  }

  override fun getDefaultPrefix(namespace: String, context: XmlFile): String? = when (namespace) {
    ANDROID_URI -> "android"
    TOOLS_URI -> "tools"
    AUTO_URI -> "app"
    AAPT_URI -> "aapt"
    XLIFF_URI -> "xliff"
    else -> null
  }
}

/** Provides element descriptors for XML tags inside Compose resource files, preventing "Element must be declared" errors. */
internal class ComposeResourcesDrawableXmlElementDescriptorProvider : XmlElementDescriptorProvider {

  override fun getDescriptor(tag: XmlTag): XmlElementDescriptor? {
    val file = tag.containingFile as? XmlFile ?: return null
    if (!file.isComposeResourcesFile()) return null
    return AnyXmlElementDescriptor(null, null)
  }
}

internal class ComposeResourcesDrawableXmlExtension : DefaultXmlExtension() {

  override fun isAvailable(file: PsiFile): Boolean = file.isComposeResourcesFile()

  override fun createTagNameReference(nameElement: ASTNode, startTagFlag: Boolean): TagNameReference =
    object : TagNameReference(nameElement, startTagFlag) {
      override fun isSoft(): Boolean = true
    }

  override fun getPrefixDeclaration(context: XmlTag, namespacePrefix: String): SchemaPrefix? {
    return super.getPrefixDeclaration(context, namespacePrefix) ?: EMPTY_SCHEMA.takeIf { namespacePrefix.isEmpty() }
  }
}

private val SCHEMA_CACHE_KEY = Key.create<ConcurrentHashMap<String, XmlFile>>("ComposeResourcesXmlSchemaProvider.schemaCache")

private fun getOrCreateSchemaFile(project: Project, namespaceUri: String): XmlFile {
  val cache = (project as UserDataHolderEx).getOrCreateUserData(SCHEMA_CACHE_KEY) { ConcurrentHashMap() }
  return cache.computeIfAbsent(namespaceUri) {
    val sanitizedName = namespaceUri.map { if (it.isLetter()) it else '_' }.joinToString("")
    val content = "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"$namespaceUri\" />"
    PsiFileFactory.getInstance(project)
      .createFileFromText("compose-resources-$sanitizedName.xsd", XMLLanguage.INSTANCE, content, false, false) as XmlFile
  }
}

private val EMPTY_SCHEMA = SchemaPrefix(null, TextRange(0, 0), "android")

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val AAPT_URI = "http://schemas.android.com/aapt"
private const val TOOLS_URI = "http://schemas.android.com/tools"
private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
private const val XLIFF_URI = "urn:oasis:names:tc:xliff:document:1.2"

private val KNOWN_NAMESPACE_URIS = setOf(ANDROID_URI, AAPT_URI, TOOLS_URI, AUTO_URI, XLIFF_URI)