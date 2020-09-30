// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ignore.lang;

import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType;

public final class HgIgnoreFileType extends IgnoreFileType {

  public static final HgIgnoreFileType INSTANCE = new HgIgnoreFileType();

  private HgIgnoreFileType() {
    super(HgIgnoreLanguage.INSTANCE);
  }
}
