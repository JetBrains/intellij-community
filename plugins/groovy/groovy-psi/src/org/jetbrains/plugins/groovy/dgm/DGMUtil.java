// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author Max Medvedev
 */
public final class DGMUtil {
  public static final String ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE = "org.codehaus.groovy.runtime.ExtensionModule";
  public static final String[] KEYS = new String[]{"moduleName", "moduleVersion", "extensionClasses", "staticExtensionClasses",};

  public static boolean isInDGMFile(PsiElement e) {
    PsiFile file = e.getContainingFile();
    return file instanceof PropertiesFile &&
           Comparing.equal(file.getName(), ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE,
                           SystemInfo.isFileSystemCaseSensitive);
  }
}
