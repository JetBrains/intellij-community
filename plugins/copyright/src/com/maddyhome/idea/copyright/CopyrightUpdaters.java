// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.util.KeyedLazyInstance;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;

/**
 * @author yole
 */
public final class CopyrightUpdaters extends FileTypeExtension<UpdateCopyrightsProvider> {
  public static final ExtensionPointName<KeyedLazyInstance<UpdateCopyrightsProvider>> EP_NAME = ExtensionPointName.create("com.intellij.copyright.updater");
  public final static CopyrightUpdaters INSTANCE = new CopyrightUpdaters();

  private CopyrightUpdaters() {
    super(EP_NAME);
  }
}
