/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class RenameAliasImportedMethodProcessor extends RenameJavaMethodProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof GroovyPsiElement && super.canProcessElement(element);
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    return RenameAliasedUsagesUtil.filterAliasedRefs(super.findReferences(element), element);
  }

  @Override
  public void findCollisions(PsiElement element,
                             final String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    final ListIterator<UsageInfo> iterator = result.listIterator();
    while (iterator.hasNext()) {
      final UsageInfo info = iterator.next();
      final PsiElement ref = info.getElement();
      if (ref == null) continue;
      if (!RenameUtil.isValidName(element.getProject(), ref, newName)) {
        iterator.add(new UnresolvableCollisionUsageInfo(ref, element) {
          @Override
          public String getDescription() {
            return RefactoringBundle.message("0.is.not.an.identifier", newName, ref.getText());
          }
        });
      }
    }
  }
}
