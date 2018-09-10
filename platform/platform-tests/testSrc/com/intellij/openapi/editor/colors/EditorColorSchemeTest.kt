/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors

import com.intellij.configurationStore.SchemeManagerFactoryBase
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
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
  val fsRule = InMemoryFsRule()

  @Test fun loadSchemes() {
    val schemeFile = fsRule.fs.getPath("colors/Foo.icls")
    val schemeData = """
    <scheme name="Foo" version="142" parent_scheme="Default">
      <option name="EDITOR_FONT_SIZE" value="12" />
      <option name="EDITOR_FONT_NAME" value="Menlo" />
      <option name="JAVA_NUMBER" baseAttributes="DEFAULT_NUMBER" />
    </scheme>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val manager = EditorColorsManagerImpl(DefaultColorSchemesManager.getInstance(), schemeManagerFactory)

    val scheme = manager.getScheme("Foo")
    assertThat(scheme.name).isEqualTo("Foo")

    (scheme as AbstractColorsScheme).setSaveNeeded(true)

    schemeManagerFactory.save()

    // JAVA_NUMBER is removed - see isParentOverwritingInheritance
    assertThat(removeSchemeMetaInfo(schemeFile.readText())).isEqualTo("""
    <scheme name="Foo" version="142" parent_scheme="Default">
      <option name="FONT_SCALE" value="${UISettings.defFontScale}" />
      <option name="LINE_SPACING" value="1.0" />
      <option name="EDITOR_FONT_SIZE" value="12" />
      <option name="EDITOR_FONT_NAME" value="${scheme.editorFontName}" />
    </scheme>""".trimIndent())
    assertThat(schemeFile.parent).hasChildren("Foo.icls")

    // test reload
    val schemeNamesBeforeReload = manager.schemeManager.allSchemes.map { it.name }
    schemeManagerFactory.process {
      it.reload()
    }

    assertThat(manager.schemeManager.allSchemes.map { it.name }).isEqualTo(schemeNamesBeforeReload)
  }
}
