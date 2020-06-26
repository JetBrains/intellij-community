// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ignore.lang;

import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreLanguage;
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax;
import org.jetbrains.annotations.NotNull;

public final class HgIgnoreLanguage extends IgnoreLanguage {
  public static final HgIgnoreLanguage INSTANCE = new HgIgnoreLanguage();

  private HgIgnoreLanguage() {
    super("HgIgnore", "hgignore");
  }

  @NotNull
  @Override
  public IgnoreFileType getFileType() {
    return HgIgnoreFileType.INSTANCE;
  }

  @Override
  public boolean isSyntaxSupported() {
    return true;
  }

  @Override
  @NotNull
  public Syntax getDefaultSyntax() {
    return Syntax.REGEXP;
  }
}
