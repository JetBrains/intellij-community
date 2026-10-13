// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.inspections

import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.lang.annotation.HighlightSeverity
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

internal class ComposeResourcesImageResourceInspectionTest : KotlinLightCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    addComposeStubs()
    addComposeResourcesStubs()
    myFixture.enableInspections(ComposeResourcesImageResourceInspection())
  }

  fun `test reports vector resource loaded with imageResource`() = assertImageResourceProblems(
    """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(imageResource(Res.drawable.vector), null)
      }
    """,
    expectedCount = 1,
  )

  fun `test reports vector resource loaded with imageResource full qualifier`() = assertImageResourceProblems(
    """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(org.jetbrains.compose.resources.imageResource(Res.drawable.vector), null)
      }
    """,
    expectedCount = 1,
    expectedHighlightedText = "org.jetbrains.compose.resources.imageResource"
  )

  fun `test reports svg resource loaded with imageResource`() = assertImageResourceProblems(
    """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.svg

      @Composable
      fun usage() {
        Image(imageResource(Res.drawable.svg), null)
      }
    """,
    expectedCount = 1,
  )

  fun `test does not report raster resource loaded with imageResource`() = assertImageResourceProblems(
    """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.raster

      @Composable
      fun usage() {
        Image(imageResource(Res.drawable.raster), null)
      }
    """,
    expectedCount = 0,
  )

  fun `test does not report another imageResource function`() = assertImageResourceProblems(
    """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import other.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(imageResource(Res.drawable.vector), null)
      }
    """,
    expectedCount = 0
  )

  fun `test reports multiple imageResource calls in same file`() = assertImageResourceProblems(
    """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import demo.shared.generated.resources.svg

      @Composable
      fun usage() {
        Image(imageResource(Res.drawable.vector), null)
        Image(imageResource(Res.drawable.svg), null)
      }
    """,
    expectedCount = 2,
  )

  fun `test replaces imageResource with painterResource for direct Image argument`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(<caret>imageResource(Res.drawable.vector), null)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painterResource(Res.drawable.vector), null)
      }
    """
  )

  fun `test replaces bitmap argument name with painter for named Image argument`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(bitmap = <caret>imageResource(Res.drawable.vector), contentDescription = null)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painter = painterResource(Res.drawable.vector), contentDescription = null)
      }
    """
  )

  fun `test replaces bitmap argument name with painter for named Image argument with full qualifier`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(bitmap = <caret>org.jetbrains.compose.resources.imageResource(Res.drawable.vector), contentDescription = null)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(painter = org.jetbrains.compose.resources.painterResource(Res.drawable.vector), contentDescription = null)
      }
    """
  )

  fun `test removes filterQuality argument for named Image argument`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import androidx.compose.ui.graphics.FilterQuality
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(bitmap = <caret>imageResource(Res.drawable.vector), contentDescription = null, filterQuality = FilterQuality.High)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painter = painterResource(Res.drawable.vector), contentDescription = null)
      }
    """
  )

  fun `test removes filterQuality argument for positional Image argument`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import androidx.compose.ui.graphics.FilterQuality
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(<caret>imageResource(Res.drawable.vector), null, filterQuality = FilterQuality.High)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painterResource(Res.drawable.vector), null)
      }
    """
  )

  fun `test removes positional filterQuality argument`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import androidx.compose.ui.Alignment
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.graphics.FilterQuality
      import androidx.compose.ui.layout.ContentScale
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(<caret>imageResource(Res.drawable.vector), null, Modifier, Alignment.Center, ContentScale.Fit, 1f, null, FilterQuality.High)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import androidx.compose.ui.Alignment
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.layout.ContentScale
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painterResource(Res.drawable.vector), null, Modifier, Alignment.Center, ContentScale.Fit, 1f, null)
      }
    """
  )

  fun `test removes filterQuality argument when Image call has argument type mismatch`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import androidx.compose.ui.graphics.FilterQuality
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(bitmap = <caret>imageResource(Res.drawable.vector), contentDescription = 42, filterQuality = FilterQuality.High)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painter = painterResource(Res.drawable.vector), contentDescription = 42)
      }
    """
  )

  fun `test keeps FilterQuality import when still used after quick fix`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import androidx.compose.ui.graphics.FilterQuality
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(<caret>imageResource(Res.drawable.vector), null, filterQuality = FilterQuality.High)
        val quality = FilterQuality.Low
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import androidx.compose.ui.graphics.FilterQuality
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painterResource(Res.drawable.vector), null)
        val quality = FilterQuality.Low
      }
    """
  )

  fun `test keeps imageResource import when still used after quick fix`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(<caret>imageResource(Res.drawable.vector), null)
        val bitmap = imageResource(Res.drawable.raster)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector
      import org.jetbrains.compose.resources.painterResource

      @Composable
      fun usage() {
        Image(painterResource(Res.drawable.vector), null)
        val bitmap = imageResource(Res.drawable.raster)
      }
    """
  )

  fun `test replaces imageResource full qualifier with painterResource for direct Image argument`() = assertImageResourceQuickFix(
    before = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(<caret>org.jetbrains.compose.resources.imageResource(Res.drawable.vector), null)
      }
    """,
    after = """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Image
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        Image(org.jetbrains.compose.resources.painterResource(Res.drawable.vector), null)
      }
    """
  )

  fun `test does not offer quick fix when imageResource is not a direct Image argument`() = assertImageResourceQuickFixUnavailable(
    """
      import androidx.compose.runtime.Composable
      import org.jetbrains.compose.resources.imageResource
      import demo.shared.generated.resources.Res
      import demo.shared.generated.resources.vector

      @Composable
      fun usage() {
        val icon = <caret>imageResource(Res.drawable.vector)
      }
    """
  )

  fun `test does not offer quick fix for custom Image function`() = assertImageResourceQuickFixUnavailable(
    """
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.graphics.ImageBitmap
    import org.jetbrains.compose.resources.imageResource
    import demo.shared.generated.resources.Res
    import demo.shared.generated.resources.vector

    @Composable
    fun Image(bitmap: ImageBitmap, contentDescription: String?) {}

    @Composable
    fun usage() {
      Image(<caret>imageResource(Res.drawable.vector), null)
    }
  """
  )

  private fun assertImageResourceProblems(
    @Language("kotlin") code: String,
    expectedCount: Int,
    expectedHighlightedText: String = "imageResource",
  ) {
    configureUsageFile(code)

    val problems = myFixture.doHighlighting(HighlightSeverity.ERROR)
      .filter { it.description == imageResourceProblemDescription }

    assertEquals(expectedCount, problems.size)
    val document = myFixture.editor.document
    problems.forEach {
      val highlightedText = document.getText(com.intellij.openapi.util.TextRange(it.startOffset, it.endOffset))
      assertEquals(expectedHighlightedText, highlightedText)
    }
  }

  private fun assertImageResourceQuickFix(@Language("kotlin") before: String, @Language("kotlin") after: String) {
    configureUsageFile(before)

    myFixture.doHighlighting()
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(imageResourceQuickFixName))

    myFixture.checkResult(after.trimIndent())
  }

  private fun assertImageResourceQuickFixUnavailable(@Language("kotlin") code: String) {
    configureUsageFile(code)

    val problems = myFixture.doHighlighting(HighlightSeverity.ERROR)
      .filter { it.description == imageResourceProblemDescription }

    assertEquals(1, problems.size)
    assertFalse(myFixture.availableIntentions.any { it.text == imageResourceQuickFixName })
  }

  private fun configureUsageFile(@Language("kotlin") code: String) {
    myFixture.configureByText("Usage.kt", code.trimIndent())
  }

  private fun addComposeStubs() {
    myFixture.addFileToProject(
      "androidx/compose/runtime/Composable.kt",
      """
        package androidx.compose.runtime

        annotation class Composable
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/foundation/Image.kt",
      """
        package androidx.compose.foundation

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Alignment
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.ColorFilter
        import androidx.compose.ui.graphics.FilterQuality
        import androidx.compose.ui.graphics.ImageBitmap
        import androidx.compose.ui.graphics.painter.Painter
        import androidx.compose.ui.layout.ContentScale

        @Composable
        fun Image(
          bitmap: ImageBitmap,
          contentDescription: String?,
          modifier: Modifier = Modifier,
          alignment: Alignment = Alignment.Center,
          contentScale: ContentScale = ContentScale.Fit,
          alpha: Float = 1f,
          colorFilter: ColorFilter? = null,
          filterQuality: FilterQuality = FilterQuality.Low,
        ) {}
        @Composable
        fun Image(
          painter: Painter,
          contentDescription: String?,
          modifier: Modifier = Modifier,
          alignment: Alignment = Alignment.Center,
          contentScale: ContentScale = ContentScale.Fit,
          alpha: Float = 1f,
          colorFilter: ColorFilter? = null,
        ) {}
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/ui/Modifier.kt",
      """
        package androidx.compose.ui

        interface Modifier {
          companion object : Modifier
        }
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/ui/Alignment.kt",
      """
        package androidx.compose.ui

        interface Alignment {
          companion object {
            val Center: Alignment = object : Alignment {}
          }
        }
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/ui/layout/ContentScale.kt",
      """
        package androidx.compose.ui.layout

        interface ContentScale {
          companion object {
            val Fit: ContentScale = object : ContentScale {}
          }
        }
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/ui/graphics/ColorFilter.kt",
      """
        package androidx.compose.ui.graphics

        class ColorFilter
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/ui/graphics/FilterQuality.kt",
      """
        package androidx.compose.ui.graphics

        class FilterQuality {
          companion object {
            val Low: FilterQuality = FilterQuality()
            val High: FilterQuality = FilterQuality()
          }
        }
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/ui/graphics/ImageBitmap.kt",
      """
        package androidx.compose.ui.graphics

        class ImageBitmap
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "androidx/compose/ui/graphics/painter/Painter.kt",
      """
        package androidx.compose.ui.graphics.painter

        class Painter
      """.trimIndent()
    )
  }

  private fun addComposeResourcesStubs() {
    myFixture.addFileToProject(
      "org/jetbrains/compose/resources/Resources.kt",
      """
        package org.jetbrains.compose.resources

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.graphics.ImageBitmap
        import androidx.compose.ui.graphics.painter.Painter

        open class Resource(val id: String, val items: Set<ResourceItem>)
        class ResourceItem(val qualifiers: Set<Any>, val path: String, val offset: Int, val size: Int)
        class DrawableResource(id: String, items: Set<ResourceItem>) : Resource(id, items)

        @Composable
        fun imageResource(resource: DrawableResource): ImageBitmap = TODO()
        @Composable
        fun painterResource(resource: DrawableResource): Painter = TODO()
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "other/ImageResource.kt",
      """
        package other

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.graphics.ImageBitmap
        import org.jetbrains.compose.resources.DrawableResource

        @Composable
        fun imageResource(resource: DrawableResource): ImageBitmap = TODO()
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "composeresources/shared/generated/resources/Res.kt",
      """
        package demo.shared.generated.resources

        import org.jetbrains.compose.resources.DrawableResource
        import org.jetbrains.compose.resources.ResourceItem

        object Res {
          object drawable
        }

        internal val Res.drawable.vector: DrawableResource by lazy {
          DrawableResource("drawable:vector", setOf(ResourceItem(setOf(), "drawable/vector.xml", -1, -1)))
        }
        internal val Res.drawable.svg: DrawableResource by lazy {
          DrawableResource("drawable:svg", setOf(ResourceItem(setOf(), "drawable/svg.svg", -1, -1)))
        }
        internal val Res.drawable.raster: DrawableResource by lazy {
          DrawableResource("drawable:raster", setOf(ResourceItem(setOf(), "drawable/raster.png", -1, -1)))
        }
      """.trimIndent()
    )
  }

  private val imageResourceProblemDescription: String
    get() = ComposeIdeBundle.message("compose.inspection.image.resource.description")

  private val imageResourceQuickFixName: String
    get() = ComposeIdeBundle.message("compose.inspection.image.resource.fix.name")
}
