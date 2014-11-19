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
package org.jetbrains.plugins.groovy.regexp;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.JavaRegExpHost;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

/**
 * @author Bas Leijdekkers
 */
public class GroovyRegExpHost extends JavaRegExpHost {

  @Override
  public boolean supportsNamedGroupSyntax(RegExpGroup group) {
    if (group.isNamedGroup()) {
      final String version = getGroovyVersion(group);
      return version != null && version.compareTo(GroovyConfigUtils.GROOVY2_0) >= 0;
    }
    return false;
  }

  @Override
  public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
    if (ref.isNamedGroupRef()) {
      final String version = getGroovyVersion(ref);
      return version != null && version.compareTo(GroovyConfigUtils.GROOVY2_0) >= 0;
    }
    return false;
  }

  @Override
  public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
    final String version = getGroovyVersion(regExpChar);
    return version != null && version.compareTo(GroovyConfigUtils.GROOVY2_0) >= 0;
  }

  private static String getGroovyVersion(PsiElement element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      return null;
    }
    return GroovyConfigUtils.getInstance().getSDKVersion(module);
  }
}
