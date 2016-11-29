/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class RegExpUtil {
  private RegExpUtil() {
  }

  @Nullable
  public static Set<String> getEnumValues(Project project, @NotNull String regExp) {
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    final PsiFile file = factory.createFileFromText("dummy.regexp", RegExpFileType.INSTANCE, regExp);
    final RegExpPattern pattern = (RegExpPattern)file.getFirstChild();
    if (pattern == null) {
      return null;
    }
    final RegExpBranch[] branches = pattern.getBranches();
    final Set<String> values = new HashSet<>();
    for (RegExpBranch branch : branches) {
      if (analyzeBranch(branch)) {
        values.add(branch.getUnescapedText());
      }
    }
    return values;
  }

  private static boolean analyzeBranch(RegExpBranch branch) {
    final RegExpAtom[] atoms = branch.getAtoms();
    for (RegExpAtom atom : atoms) {
      if (!(atom instanceof RegExpChar) || ((RegExpChar)atom).getValue() == null) {
        return false;
      }
      else if (((RegExpChar)atom).getType() != RegExpChar.Type.CHAR) {
        // this could probably allow more, such as escape sequences
        return false;
      }
    }
    return true;
  }
}