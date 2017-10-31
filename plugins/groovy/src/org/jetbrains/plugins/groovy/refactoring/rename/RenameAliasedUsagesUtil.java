// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyFileImports;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImports;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyNamedImport;
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
    if (containingFile instanceof GroovyFile && findAliasedName(aliases, ((GroovyFile)containingFile), member) != EMPTY_ALIAS) {
      if (PsiTreeUtil.getParentOfType(element, GrImportStatement.class, true) != null) return false;
      return true;
    }
    return false;
  }

  private static String findAliasedName(Map<GroovyFile, String> map, GroovyFile containingFile, PsiElement elementToResolve) {
    final String s = map.get(containingFile);
    if (s != null) return s;

    final PsiManager manager = elementToResolve.getManager();
    final ResolverProcessor processor = getProcessor(elementToResolve, containingFile);
    final GroovyFileImports fileImports = GroovyImports.getImports(containingFile);
    for (GroovyNamedImport anImport : fileImports.getAllNamedImports()) {
      if (!anImport.isAliased()) continue;
      anImport.processDeclarations(processor, ResolveState.initial(), containingFile, containingFile);
      final GroovyResolveResult[] results = processor.getCandidates();
      for (GroovyResolveResult result : results) {
        if (manager.areElementsEquivalent(elementToResolve, result.getElement())) {
          final String importedName = anImport.getName();
          map.put(containingFile, importedName);
          return importedName;
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
