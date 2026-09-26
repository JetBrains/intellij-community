// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config

import com.intellij.framework.library.DownloadableLibraryType
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.util.download.DownloadableFileSetVersions.FileSetVersionsCallback
import icons.JetgroovyIcons
import org.jetbrains.plugins.groovy.GroovyBundle
import javax.swing.Icon

internal class GroovyDownloadableLibraryType :
  DownloadableLibraryType(GroovyBundle.messagePointer("language.groovy"), "groovy-sdk", "groovy",
                          GroovyDownloadableLibraryType::class.java.getResource("groovy.sdk.xml")) {
  override fun getDetectionClassNames(): Array<String> = arrayOf("groovy.lang.GroovyObject")

  override fun getLibraryTypeIcon(): Icon = JetgroovyIcons.Groovy.Groovy_16x16
}

/**
 * May be executed on EDT
 */
fun loadLatestGroovyVersions(callback : FileSetVersionsCallback<FrameworkLibraryVersion>) {
  val groovyLibraryType = LibraryType.EP_NAME.findExtensionOrFail(GroovyDownloadableLibraryType::class.java)
  val downloadableLibraryDescription = groovyLibraryType.libraryDescription
  downloadableLibraryDescription.fetchVersions(callback)
}
