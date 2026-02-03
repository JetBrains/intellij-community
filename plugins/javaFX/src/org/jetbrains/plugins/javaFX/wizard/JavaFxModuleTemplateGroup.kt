// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import org.jetbrains.plugins.javaFX.JavaFXBundle

internal class JavaFxModuleTemplateGroup : FileTemplateGroupDescriptorFactory {
  override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
    val root = FileTemplateGroupDescriptor(JavaFXBundle.JAVA_FX, AllIcons.Nodes.Module)

    root.addTemplate(JAVAFX_POM_XML)
    root.addTemplate(JAVAFX_MVNW_PROPERTIES)
    root.addTemplate(JAVAFX_BUILD_GRADLE_KTS)
    root.addTemplate(JAVAFX_SETTINGS_GRADLE_KTS)
    root.addTemplate(JAVAFX_GRADLEW_PROPERTIES)
    root.addTemplate(JAVAFX_HELLO_VIEW_FXML)
    root.addTemplate(JAVAFX_MODULE_INFO_JAVA)

    root.addTemplate("javafx-Launcher-java.java")
    root.addTemplate("javafx-Launcher-groovy.groovy")

    root.addTemplate("javafx-HelloApplication-java.java")
    root.addTemplate("javafx-HelloApplication-kotlin.kt")
    root.addTemplate("javafx-HelloApplication-groovy.groovy")

    root.addTemplate("javafx-HelloController-java.java")
    root.addTemplate("javafx-HelloController-kotlin.kt")
    root.addTemplate("javafx-HelloController-groovy.groovy")

    return root
  }

  companion object {
    const val JAVAFX_BUILD_GRADLE_KTS = "javafx-build.gradle.kts"
    const val JAVAFX_POM_XML = "javafx-pom.xml"
    const val JAVAFX_MVNW_PROPERTIES = "javafx-maven-wrapper.properties"
    const val JAVAFX_SETTINGS_GRADLE_KTS = "javafx-settings.gradle.kts"
    const val JAVAFX_GRADLEW_PROPERTIES = "javafx-gradle-wrapper.properties"

    const val JAVAFX_HELLO_VIEW_FXML = "javafx-hello-view.fxml"
    const val JAVAFX_MODULE_INFO_JAVA = "javafx-module-info.java"
  }
}
