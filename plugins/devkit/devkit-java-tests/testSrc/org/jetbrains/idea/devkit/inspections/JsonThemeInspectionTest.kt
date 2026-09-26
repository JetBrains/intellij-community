// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.impl.DefaultUsageSearchParameters
import com.intellij.json.psi.JsonProperty
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.psi.impl.allDeclarationsInElement
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.ColorIcon
import org.jetbrains.idea.devkit.themes.ThemeColorKey
import org.jetbrains.idea.devkit.themes.UnresolvedThemeJsonNamedColorInspection
import org.jetbrains.idea.devkit.themes.UnresolvedThemeKeyInspection
import org.jetbrains.idea.devkit.themes.UnusedThemeJsonNamedColorInspection
import java.awt.Color

class JsonThemeInspectionTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(
      UnresolvedThemeKeyInspection::class.java,
      UnresolvedThemeJsonNamedColorInspection::class.java,
      UnusedThemeJsonNamedColorInspection::class.java
    )
  }

  fun testUnusedNamedColors() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "colors": {
          "used-color": "#000000",
          <warning descr="Named color 'unused-color' is never used">"unused-color"</warning>: "#FFFFFF"
        },
        "ui": {
          "ActionButton.focusedBorderColor": "used-color"
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testUnusedPaletteColors() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "colors": {
          "blue-1": "#0000FF",
          "gray-500": "#808080",
          "red-unknown": "#FF0000"
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testUnusedNamedColorDeclaredInParent() {
    myFixture.addFileToProject("parent.theme.json", """
      {
        "name": "Parent Theme",
        "colors": {
          "parent-color": "#000000"
        }
      }
    """.trimIndent())

    myFixture.configureByText("child.theme.json", """
      {
        "name": "Child Theme",
        "parentTheme": "Parent Theme",
        "colors": {
          "parent-color": "#FFFFFF"
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testRegularKeys() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "ui": {
          "ActionButton": {
            "focusedBorderColor": "#000000",
            <warning descr="Unresolved key 'ActionButton.unknownKey'">"unknownKey"</warning>: "#FFFFFF"
          }
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testColorNGradientKeys() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "ui": {
          "ProjectGradients": {
            "Group1": {
              "DiagonalGradient": {
                "Color1": "#000000",
                <warning descr="Unresolved key 'ProjectGradients.Group1.DiagonalGradient.UnknownColor1'">"UnknownColor1"</warning>: "#ffffff"
              },
              "UnknownGradient": {
                <warning descr="Unresolved key 'ProjectGradients.Group1.UnknownGradient.Color1'">"Color1"</warning>: "#000000"
              }
            }
          }
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testFractionNGradientKeys() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "ui": {
          "ProjectGradients": {
            "Group2": {
              "DiagonalGradient": {
                "Fraction1": 0.5,
                <warning descr="Unresolved key 'ProjectGradients.Group2.DiagonalGradient.UnknownFraction1'">"UnknownFraction1"</warning>: 0.1
              },
              "UnknownGradient": {
                <warning descr="Unresolved key 'ProjectGradients.Group2.UnknownGradient.Fraction1'">"Fraction1"</warning>: 0.5
              }
            }
          }
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testColorNamesInThemeFiles() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "colors": {
          "registered-color": "#000000"
        },
        "ui": {
          "ActionButton.focusedBorderColor": "registered-color",
          "ActionButton.separatorColor": "#ffffff",
          "ActionButton": {
            "pressedBackground": "<error descr="Cannot resolve symbol 'unregistered-color'">unregistered-color</error>",
            "hoverBackground": "registered-color"
          }
        },
        "ProjectGradients": {
          "Group1": {
            "DiagonalGradient": {
              "Color1": "registered-color",
              "Color2": "<error descr="Cannot resolve symbol 'unregistered-color'">unregistered-color</error>"
            }
          }
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testNamedColorFromParentTheme() {
    myFixture.addFileToProject("parent.theme.json", """
      {
        "name": "Parent Theme",
        "colors": {
          "parent-color": "#000000"
        }
      }
    """.trimIndent())

    myFixture.configureByText("test.theme.json", """
      {
        "name": "Child Theme",
        "parentTheme": "Parent Theme",
        "ui": {
          "ActionButton.focusedBorderColor": "parent-color",
          "ActionButton.separatorColor": "<error descr="Cannot resolve symbol 'unknown-color'">unknown-color</error>"
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testNamedColorFromThemeProviderId() {
    myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <id>test.plugin</id>
        
        <extensionPoints>
          <extensionPoint qualifiedName="com.intellij.themeProvider" interface="com.intellij.ide.ui.LafManagerListener"/>
        </extensionPoints>
        
        <extensions defaultExtensionNs="com.intellij">
          <themeProvider id="MyCustomId" path="/themes/my_theme.theme.json"/>
        </extensions>
      </idea-plugin>
    """.trimIndent())

    myFixture.addFileToProject("themes/my_theme.theme.json", """
      {
        "name": "My Theme Name",
        "colors": {
          "provider-color": "#000000"
        }
      }
    """.trimIndent())

    myFixture.configureByText("child.theme.json", """
      {
        "name": "Child Theme",
        "parentTheme": "MyCustomId",
        "ui": {
          "ActionButton.focusedBorderColor": "provider-color",
          "ActionButton.separatorColor": "<error descr="Cannot resolve symbol 'unknown-color'">unknown-color</error>"
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testCyclicColorDefinitions() {
    // they do not break IDE, but no inspection to prevent it yet
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "colors": {
          "color1": "color2",
          "color2": "color1"
        },
        "ui": {
          "ActionButton.focusedBorderColor": "color1"
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testBorderSoftReference() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "colors": {
          "registered-color": "#000000"
        },
        "ui": {
          "ActionButton.focusedBorderColor": "registered-color",
          "ActionButton.focusedBorderColor": "<error descr="Cannot resolve symbol 'unknown-color'">unknown-color</error>",
          <warning descr="Unresolved key 'ActionButton.unregisteredBorder'">"ActionButton.unregisteredBorder"</warning>: "unregisteredBorder",
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testLineMarkersColorHex() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "ui": {
          "ActionButton.focusedBorderColor": "#FF0000",
          "ActionButton.separatorColor": "#00FF00AA"
        }
      }
    """.trimIndent())

    val colorIcons = myFixture.findAllGutters()
      .distinct()
      .mapNotNull { it.icon as? ColorIcon }
    assertEquals(2, colorIcons.size)
  }

  fun testLineMarkersNamedColorResolvable() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "colors": {
          "my-color": "#FF0000"
        },
        "ui": {
          "ActionButton.focusedBorderColor": "my-color"
        }
      }
    """.trimIndent())

    // findAllGutters() may double-count annotator-based gutters; distinct() deduplicates via GutterIconRenderer.equals()
    val colorIcons = myFixture.findAllGutters().distinct().mapNotNull { it.icon as? ColorIcon }
    // "#FF0000" value in colors section + "my-color" reference resolved to #FF0000
    assertEquals(2, colorIcons.size)
    assertTrue(colorIcons.all { it.iconColor == Color.RED })
  }

  fun testLineMarkersNamedColorUnresolvable() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "ui": {
          "ActionButton.focusedBorderColor": "unknown-color"
        }
      }
    """.trimIndent())

    val colorIconCount = myFixture.findAllGutters().count { it.icon is ColorIcon }
    assertEquals(0, colorIconCount)
  }

  fun testLineMarkersNamedColorFromParentTheme() {
    myFixture.addFileToProject("parent.theme.json", """
      {
        "name": "Parent Theme",
        "colors": {
          "parent-color": "#0000FF"
        }
      }
    """.trimIndent())

    myFixture.configureByText("child.theme.json", """
      {
        "name": "Child Theme",
        "parentTheme": "Parent Theme",
        "ui": {
          "ActionButton.focusedBorderColor": "parent-color"
        }
      }
    """.trimIndent())

    val colorIcons = myFixture.findAllGutters()
      .distinct()
      .mapNotNull { it.icon as? ColorIcon }
    assertEquals(1, colorIcons.size)
    assertEquals(Color.BLUE, colorIcons[0].iconColor)
  }

  fun testNavigationToNamedColor() {
    myFixture.configureByText("test.theme.json", """
      {
        "name": "Test Theme",
        "colors": {
          "my-color": "#FF0000"
        },
        "ui": {
          "ActionButton.focusedBorderColor": "my-co<caret>lor"
        }
      }
    """.trimIndent())

    val host = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
    val reference = PsiSymbolReferenceService.getService().getReferences(host).firstOrNull()
    assertNotNull(reference)
    val symbol = (reference!!.resolveReference().firstOrNull() ?: fail("Symbol not resolved")) as ThemeColorKey
    assertEquals("my-color", symbol.colorKey)

    val navigationTarget = symbol as NavigationTarget
    val navigationRequest = PlatformTestUtil.callOnBgtSynchronously(
      {
        runReadActionBlocking {
          navigationTarget.navigationRequest()
        }
      }, 10) as NavigationRequest

    assertNotNull(navigationRequest)
    val sourceNavigationRequest = navigationRequest as SourceNavigationRequest

    assertEquals("test.theme.json", sourceNavigationRequest.file.name)
  }

  fun testFindUsagesForNamedColor() {
    val fileText = """
      {
        "name": "Test Theme",
        "colors": {
          "my-color": "#FF0000"
        },
        "ui": {
          "ActionButton.focusedBorderColor": "my-color",
          "ActionButton.separatorColor": "my-color"
        }
      }
    """.trimIndent()
    myFixture.configureByText("test.theme.json", fileText)

    val element = myFixture.file.findElementAt(fileText.indexOf("my-color"))!!.parentOfType<JsonProperty>()!!
    val declarations = allDeclarationsInElement(element)
    assertEquals(1, declarations.size)

    val symbol = declarations.first().symbol as ThemeColorKey
    assertEquals("my-color", symbol.colorKey)

    val usages: Collection<Usage> = PlatformTestUtil.callOnBgtSynchronously(
      {
        runReadActionBlocking {
          val parameters = DefaultUsageSearchParameters(project, symbol, GlobalSearchScope.fileScope(myFixture.file))
          SearchService.getInstance().searchParameters(parameters).findAll()
        }
      }, 10)!!

    assertEquals(2, usages.size)
    for (usage in usages.map { it as PsiUsage }) {
      assertFalse("usage must not be declaration", usage.declaration)
      assertEquals("test.theme.json", usage.file.name)
    }
  }
}