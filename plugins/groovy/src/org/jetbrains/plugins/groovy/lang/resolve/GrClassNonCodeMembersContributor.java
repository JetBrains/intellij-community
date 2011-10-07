package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class GrClassNonCodeMembersContributor extends NonCodeMembersContributor {
  
  private static final LightCacheKey<String[]> KEY = LightCacheKey.create();
  
  @Override
  public void processDynamicElements(@NotNull final PsiType qualifierType,
                                     final PsiScopeProcessor processor,
                                     final GroovyPsiElement place,
                                     final ResolveState state) {
    final PsiClass aClass = PsiTypesUtil.getPsiClass(qualifierType);
    if (aClass == null) return;

    String[] superClassNames = KEY.getCachedValue(aClass);
    if (superClassNames == null) {
      Set<PsiClass> superClasses = new HashSet<PsiClass>();
      superClasses.add(aClass);
      InheritanceUtil.getSuperClasses(aClass, superClasses, true);
      
      
      superClassNames = new String[superClasses.size()];
      int i = 0;
      for (PsiClass superClass : superClasses) {
        superClassNames[i++] = superClass.getQualifiedName();
      }
      
      superClassNames = KEY.putCachedValue(aClass, superClassNames);
    }

    final MultiMap<String, GrClassEnhancer> enhancers = GrClassEnhancer.getEnhancers();

    for (String superClassName : superClassNames) {
      for (GrClassEnhancer enhancer : enhancers.get(superClassName)) {
        if (!enhancer.processDynamicElements(qualifierType, aClass, processor, place, state)) return;
      }
    }
  }
}
