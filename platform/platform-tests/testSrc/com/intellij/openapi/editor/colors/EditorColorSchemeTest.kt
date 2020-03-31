// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors

import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.AdditionalTextAttributesEP
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.readText
import com.intellij.util.io.write
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class EditorColorSchemeTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test
  fun loadSchemes() {
    val schemeFile = fsRule.fs.getPath("colors/Foo.icls")
    val schemeData = """
    <scheme name="Foo" version="142" parent_scheme="Default">
      <option name="EDITOR_FONT_SIZE" value="12" />
      <option name="EDITOR_FONT_NAME" value="Menlo" />
      <option name="JAVA_NUMBER" baseAttributes="DEFAULT_NUMBER" />
    </scheme>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val manager = EditorColorsManagerImpl(schemeManagerFactory)

    val scheme = manager.getScheme("Foo")
    assertThat(scheme.name).isEqualTo("Foo")

    (scheme as AbstractColorsScheme).setSaveNeeded(true)

    schemeManagerFactory.save()

    // JAVA_NUMBER is removed - see isParentOverwritingInheritance
    assertThat(removeSchemeMetaInfo(schemeFile.readText())).isEqualTo("""
    <scheme name="Foo" version="142" parent_scheme="Default">
      <option name="FONT_SCALE" value="${UISettings.defFontScale}" />
      <option name="LINE_SPACING" value="1.2" />
      <option name="EDITOR_FONT_SIZE" value="12" />
      <option name="EDITOR_FONT_NAME" value="${scheme.editorFontName}" />
    </scheme>""".trimIndent())
    assertThat(schemeFile.parent).hasChildren("Foo.icls")

    // test reload
    val schemeNamesBeforeReload = manager.schemeManager.allSchemes.map { it.name }
    schemeManagerFactory.process {
      it.reload()
    }

    assertThat(manager.schemeManager.allSchemes
      .map { it.name })
      .isEqualTo(schemeNamesBeforeReload)
  }

  @Test
  fun optimizeBundledSchemes() {
    val schemeFile = fsRule.fs.getPath("colors/Foo.icls")
    val schemeData = """
    <scheme name="Foo" version="142" parent_scheme="Darcula">
      <metaInfo>
        <property name="forceOptimize">true</property>
      </metaInfo>
      <attributes>
        <option baseAttributes="DEFAULT_CLASS_NAME" name="GO_BUILTIN_TYPE_REFERENCE" />
      </attributes>
    </scheme>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val manager = EditorColorsManagerImpl(schemeManagerFactory)

    val scheme = manager.getScheme("Foo")
    assertThat(scheme.name).isEqualTo("Foo")

    (scheme as AbstractColorsScheme).setSaveNeeded(true)

    schemeManagerFactory.save()

    // GO_BUILTIN_TYPE_REFERENCE should be removed as it's the same as defined in parent scheme
    assertThat(removeSchemeMetaInfo(schemeFile.readText())).isEqualTo("""
      <scheme name="Foo" version="142" parent_scheme="Darcula">
      </scheme>""".trimIndent())
  }

  @Test
  fun loadAdditionalAttributesBeforeOptimization() {
    val ep = AdditionalTextAttributesEP()
    ep.scheme = "Darcula"
    ep.file = "com/intellij/openapi/editor/colors/foregroundForGoBuiltinTypeReference.xml"
    AdditionalTextAttributesEP.EP_NAME.getPoint(null).registerExtension(ep, disposableRule.disposable)

    val schemeFile = fsRule.fs.getPath("colors/Foo.icls")
    val schemeData = """
    <scheme name="Foo" version="142" parent_scheme="Darcula">
      <metaInfo>
        <property name="forceOptimize">true</property>
      </metaInfo>
      <attributes>
        <option baseAttributes="DEFAULT_CLASS_NAME" name="GO_BUILTIN_TYPE_REFERENCE" />
      </attributes>
    </scheme>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val manager = EditorColorsManagerImpl(schemeManagerFactory)

    val scheme = manager.getScheme("Foo")
    assertThat(scheme.name).isEqualTo("Foo")

    (scheme as AbstractColorsScheme).setSaveNeeded(true)

    schemeManagerFactory.save()

    // GO_BUILTIN_TYPE_REFERENCE should not be removed as it's not the same as defined in foregroundForGoBuiltinTypeReference
    assertThat(removeSchemeMetaInfo(schemeFile.readText())).isEqualTo("""
      <scheme name="Foo" version="142" parent_scheme="Darcula">
        <attributes>
          <option name="GO_BUILTIN_TYPE_REFERENCE" baseAttributes="" />
        </attributes>
      </scheme>""".trimIndent())
  }
}
