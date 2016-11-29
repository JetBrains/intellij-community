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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class RenameAliasedUsagesUtil {
  private static final String EMPTY_ALIAS = "____00_______EMPTY_ALIAS_______00____";

  private RenameAliasedUsagesUtil() {
  }

  public static Collection<PsiReference> filterAliasedRefs(Collection<PsiReference> refs, PsiElement element) {
    Map<GroovyFile, String> aliases = new HashMap<>();

    ArrayList<PsiReference> result = new ArrayList<>();

    for (PsiReference ref : refs) {
      final PsiElement e = ref.getElement();
      if (e == null) continue;
      if (skipReference(element, aliases, e)) continue;
      result.add(ref);
    }
    return result;

  }

  public static boolean skipReference(PsiElement member, Map<GroovyFile, String> aliases, PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof GroovyFile && findAliasedName(aliases, (GroovyFile)element.getContainingFile(), member) != EMPTY_ALIAS) {
      if (PsiTreeUtil.getParentOfType(element, GrImportStatement.class, true) != null) return false;
      return true;
    }
    return false;
  }

  private static String findAliasedName(Map<GroovyFile, String> map, GroovyFile containingFile, PsiElement elementToResolve) {
    final String s = map.get(containingFile);
    if (s != null) return s;
    final GrImportStatement[] imports = containingFile.getImportStatements();
    final PsiManager manager = elementToResolve.getManager();
    for (GrImportStatement anImport : imports) {
      if (anImport.isAliasedImport()) {
        final ResolverProcessor processor = getProcessor(elementToResolve, containingFile);
        anImport.processDeclarations(processor, ResolveState.initial(), null, containingFile);
        final GroovyResolveResult[] results = processor.getCandidates();
        for (GroovyResolveResult result : results) {
          if (manager.areElementsEquivalent(elementToResolve, result.getElement())) {
            final String importedName = anImport.getImportedName();
            if (importedName != null) {
              map.put(containingFile, importedName);
              return importedName;
            }
          }
        }
      }
    }
    map.put(containingFile, EMPTY_ALIAS);
    return EMPTY_ALIAS;
  }

  public static ResolverProcessor getProcessor(PsiElement element, GroovyPsiElement place) {
    if (element instanceof PsiMethod) {
      return new MethodResolverProcessor(null, place, false, null, null, PsiType.EMPTY_ARRAY);
    }
    else if (element instanceof PsiField) {
      return new PropertyResolverProcessor(null, place);
    }
    else if (element instanceof PsiClass) {
      return new ClassResolverProcessor(null, place);
    }
    throw new IllegalArgumentException("element must be method or field or class: " + element.getClass() + ", text=" + element.getText());
  }
}
