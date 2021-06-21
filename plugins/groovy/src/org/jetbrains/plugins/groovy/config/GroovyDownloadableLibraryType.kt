// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.framework.library.DownloadableLibraryType
import icons.JetgroovyIcons
import org.jetbrains.plugins.groovy.GroovyBundle
import javax.swing.Icon

class GroovyDownloadableLibraryType :
  DownloadableLibraryType(GroovyBundle.messagePointer("language.groovy"), "groovy-sdk", "groovy",
                          GroovyDownloadableLibraryType::class.java.getResource("groovy.sdk.xml")) {
  override fun getDetectionClassNames(): Array<String> = arrayOf("groovy.lang.GroovyObject")

  override fun getLibraryTypeIcon(): Icon = JetgroovyIcons.Groovy.Groovy_16x16
}
