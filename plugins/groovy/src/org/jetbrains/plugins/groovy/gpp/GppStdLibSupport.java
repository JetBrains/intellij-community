package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.resolve.DominanceAwareMethod;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class GppStdLibSupport implements NonCodeMembersProcessor {
  private static final Key<CachedValue<Map<String, List<PsiMethod>>>> CACHED_STDLIB = Key.create("GppStdLib");
  private static final String[] STDLIB_CLASSES =  {
    "groovy.util.Conversions",
    "groovy.util.Files", "groovy.util.Filters",
    "groovy.util.Iterations", "groovy.util.Mappers",
    "groovy.util.Sort", "groovy.util.Strings",
    "groovy.util.With",
    "groovy.util.concurrent.Atomics",
    "org.mbte.groovypp.runtime.ArraysMethods",
    "org.mbte.groovypp.runtime.DefaultGroovyPPMethods"};

  public boolean processNonCodeMembers(PsiType type, PsiScopeProcessor processor, PsiElement place, boolean forCompletion) {
    if (!(type instanceof PsiClassType)) {
      return true;
    }
    if (!GppTypeConverter.hasTypedContext(place)) {
      return true;
    }

    final String className = TypeConversionUtil.erasure(type).getCanonicalText();

    final Project project = place.getProject();
    final Map<String, List<PsiMethod>> map = CachedValuesManager.getManager(project).getCachedValue(project, CACHED_STDLIB, new CachedValueProvider<Map<String, List<PsiMethod>>>() {
        public Result<Map<String, List<PsiMethod>>> compute() {
          final GroovyPsiManager manager = GroovyPsiManager.getInstance(project);
          final Map<String, List<PsiMethod>> result = new HashMap<String, List<PsiMethod>>();
          final NotNullFunction<PsiMethod, PsiMethod> nonStaticConverter = new NotNullFunction<PsiMethod, PsiMethod>() {
            @NotNull
            public PsiMethod fun(PsiMethod method) {
              return new GppGdkMethod(method, false);
            }
          };

          for (String qname : STDLIB_CLASSES) {
            manager.addCategoryMethods(qname, result, nonStaticConverter);
          }

          manager.addCategoryMethods("org.mbte.groovypp.runtime.DefaultGroovyPPStaticMethods", result, new NotNullFunction<PsiMethod, PsiMethod>() {
            @NotNull
            public PsiMethod fun(PsiMethod method) {
              return new GppGdkMethod(method, true);
            }
          });

          return Result.create(result, ProjectRootManager.getInstance(project));
        }
      }, false);
    final List<PsiMethod> methods = map.get(className);
    if (methods == null) {
      return true;
    }

    for (PsiMethod method : methods) {
      if (!ResolveUtil.processElement(processor, method)) {
        return false;
      }
    }
    return true;
  }

  private static class GppGdkMethod extends GrGdkMethodImpl implements DominanceAwareMethod {
    public GppGdkMethod(PsiMethod method, final boolean isStatic) {
      super(method, isStatic);
    }

    public boolean isMoreConcreteThan(@NotNull final PsiSubstitutor substitutor,
                             @NotNull PsiMethod another,
                             @NotNull PsiSubstitutor anotherSubstitutor,
                             @NotNull GroovyPsiElement context) {
      if (another instanceof GrGdkMethodImpl && another.getName().equals(getName())) {
        final PsiParameter[] plusParameters = getParameterList().getParameters();
        final PsiParameter[] defParameters = another.getParameterList().getParameters();

        final PsiType[] paramTypes = new PsiType[plusParameters.length];
        for (int i = 0; i < paramTypes.length; i++) {
          paramTypes[i] = eliminateOneMethodInterfaces(plusParameters[i], defParameters, i);

        }

        final GrClosureSignature gdkSignature = GrClosureSignatureUtil.createSignature(another, anotherSubstitutor);
        if (GrClosureSignatureUtil.isSignatureApplicable(gdkSignature, paramTypes, context)) {
          return true;
        }
      }
      return false;
    }

    private static PsiType eliminateOneMethodInterfaces(PsiParameter plusParameter, PsiParameter[] gdkParameters, int i) {
      PsiType type = plusParameter.getType();
      if (i < gdkParameters.length &&
          gdkParameters[i].getType().equalsToText(GrClosableBlock.GROOVY_LANG_CLOSURE) &&
          GppClosureParameterTypeProvider.findSingleAbstractMethodSignature(type) != null) {
        return gdkParameters[i].getType();
      }
      return type;
    }
  }
}
