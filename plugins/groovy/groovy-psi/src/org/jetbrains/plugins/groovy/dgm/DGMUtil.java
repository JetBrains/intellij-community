/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author Max Medvedev
 */
public class DGMUtil {
  public static final String[] KEYS = new String[]{"moduleName", "moduleVersion", "extensionClasses", "staticExtensionClasses",};

  public static boolean isInDGMFile(PsiElement e) {
    PsiFile file = e.getContainingFile();
    return file instanceof PropertiesFile &&
           Comparing.equal(file.getName(), GroovyExtensionProvider.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE,
                           SystemInfo.isFileSystemCaseSensitive);
  }
}
