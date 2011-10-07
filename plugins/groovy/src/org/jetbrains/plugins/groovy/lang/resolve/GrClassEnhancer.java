package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Sergey Evdokimov
 */
public abstract class GrClassEnhancer {
  
  private static final ExtensionPointName<GrClassEnhancer> EP_NAME = ExtensionPointName.create("org.intellij.groovy.classEnhancer");

  private static volatile MultiMap<String, GrClassEnhancer> ourEnhancers;
  
  @NotNull
  public abstract String getQualifiedName();

  public abstract boolean processDynamicElements(@NotNull PsiType qualifierType,
                                              @NotNull PsiClass aClass,
                                              PsiScopeProcessor processor,
                                              GroovyPsiElement place,
                                              ResolveState state);

  public static MultiMap<String, GrClassEnhancer> getEnhancers() {
    MultiMap<String, GrClassEnhancer> res = ourEnhancers;
    if (ourEnhancers == null) {
      res = new MultiMap<String, GrClassEnhancer>();

      for (GrClassEnhancer enhancer : EP_NAME.getExtensions()) {
        res.putValue(enhancer.getQualifiedName(), enhancer);
      }
      
      ourEnhancers = res;
    }
    return res;
  }
  
}
