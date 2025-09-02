// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import org.editorconfig.configmanagement.extended.EditorConfigIntellijNameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorConfigCompletionWeigher extends LookupElementWeigher {

  public EditorConfigCompletionWeigher() {
    super("editorConfigWeigher");
  }

  @Override
  public @Nullable Boolean weigh(@NotNull LookupElement element) {
    return element.getLookupString().startsWith(EditorConfigIntellijNameUtil.IDE_PREFIX);
  }
}
