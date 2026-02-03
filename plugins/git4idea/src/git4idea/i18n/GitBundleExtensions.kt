// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.i18n

import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey

object GitBundleExtensions {
  fun messagePointer(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) key: String, vararg params: Any): () -> String = {
    GitBundle.message(key, *params)
  }

  @Nls
  @JvmStatic
  fun html(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) key: String, vararg params: Any): String =
    HtmlChunk.raw(GitBundle.message(key, *params)).wrapWith(HtmlChunk.html()).toString()
}
