@file:OptIn(InternalResourceApi::class)
package org.example.project

import org.jetbrains.compose.resources.*

// the build dir is not available in the tests so here we emulate the generated content
// should be modified to match the composeResources dirs content data
internal object Res {
  public object drawable {
    internal val compose_multiplatform: DrawableResource by lazy { DrawableResource("drawable:compose_multiplatform", setOf(ResourceItem(setOf(), "drawable/compose-multiplatform.xml", -1, -1), ResourceItem(setOf(DensityQualifier.XXHDPI, ), "drawable-xxhdpi/compose-multiplatform.xml", -1, -1))) }
    internal val test: DrawableResource by lazy { DrawableResource("drawable:test", setOf(ResourceItem(setOf(), "drawable/test.png", -1, -1), )) }
  }

  public object string {
    internal val test: StringResource by lazy { StringResource("string:test", "test", setOf(ResourceItem(setOf(LanguageQualifier("ro"), ),"values-ro/strings.xml",-1,-1), ResourceItem(setOf(LanguageQualifier("au"), RegionQualifier("US"), ),"values-au-rUS/strings.xml",-1,-1), ResourceItem(setOf(),"values/strings.xml",-1,-1), ResourceItem(setOf(LanguageQualifier("nl"), ),"values-nl/strings.xml",-1,-1))) }
  }

  public object array {
    internal val test: StringArrayResource by lazy { StringArrayResource("string-array:test", "test", setOf(ResourceItem(setOf(),"values/strings.xml",-1,-1))) }
  }

  public object plurals {
    internal val test: PluralStringResource by lazy { PluralStringResource("plurals:test", "test", setOf(ResourceItem(setOf(),"values/strings.xml",-1,-1))) }
  }

  public object font {
    internal val test: FontResource by lazy { FontResource("font:test", setOf(ResourceItem(setOf(),"font/test.ttf",-1,-1))) }
  }
}