package com.maddyhome.idea.copyright;

import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightInstanceFactory;

/**
 * @author yole
 */
public class CopyrightUpdaters extends FileTypeExtension<UpdateCopyrightInstanceFactory> {
  public static CopyrightUpdaters INSTANCE = new CopyrightUpdaters();

  private CopyrightUpdaters() {
    super("com.intellij.copyright.updaters");
  }
}
