// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Max Medvedev
 */
public class GdkMethodHolder {
  private static final Key<CachedValue<GdkMethodHolder>> CACHED_NON_STATIC = Key.create("Cached instance gdk method holder");
  private static final Key<CachedValue<GdkMethodHolder>> CACHED_STATIC = Key.create("Cached static gdk method holder");

  private final String myClassName;
  private final ConcurrentMap<String, MultiMap<String, PsiMethod>> myOriginalMethodsByNameAndType;
  private final NotNullLazyValue<MultiMap<String, PsiMethod>> myOriginalMethodByType;
  private final boolean myStatic;

  private GdkMethodHolder(final PsiClass categoryClass, final boolean isStatic) {
    myClassName = categoryClass.getName();
    myStatic = isStatic;
    final MultiMap<String, PsiMethod> byName = new MultiMap<>();
    for (PsiMethod m : categoryClass.getMethods()) {
      final PsiParameter[] params = m.getParameterList().getParameters();
      if (params.length == 0) continue;
      if (params[0].getType() instanceof PsiPrimitiveType) continue;
      if (!m.hasModifierProperty(PsiModifier.PUBLIC) || !m.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (PsiImplUtil.isDeprecatedByAnnotation(m) || PsiImplUtil.isDeprecatedByDocTag(m)) {
        continue;
      }
      byName.putValue(m.getName(), m);
    }
    myOriginalMethodByType = VolatileNotNullLazyValue.createValue(() -> groupByType(byName.values()));
    myOriginalMethodsByNameAndType = ConcurrentFactoryMap.createMap(name -> groupByType(byName.get(name)));
  }

  @NotNull
  private static MultiMap<String, PsiMethod> groupByType(Collection<? extends PsiMethod> methods) {
    MultiMap<String, PsiMethod> map = new MultiMap<>();
    for (PsiMethod method : methods) {
      PsiType type = getCategoryTargetType(method);
      if (type == null) continue;
      map.putValue(type.getCanonicalText(), method);
    }
    return map;
  }

  @Nullable
  private static PsiType getCategoryTargetType(@NotNull PsiMethod method) {
    final PsiType parameterType = method.getParameterList().getParameters()[0].getType();
    return TypeConversionUtil.erasure(parameterType);
  }

  public boolean processMethods(PsiScopeProcessor processor, @NotNull ResolveState state, PsiType qualifierType, Project project) {
    if (qualifierType == null) return true;
    if (state.get(ClassHint.STATIC_CONTEXT) == Boolean.TRUE && !myStatic) return true;

    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(state);
    final MultiMap<String, PsiMethod> map = name != null ? myOriginalMethodsByNameAndType.get(name) : myOriginalMethodByType.getValue();
    if (map.isEmpty()) {
      return true;
    }

    for (String superType : ResolveUtil.getAllSuperTypes(qualifierType, project)) {
      for (PsiMethod method : map.get(superType)) {
        String info = GdkMethodUtil.generateOriginInfo(method);
        GrGdkMethod gdk = GrGdkMethodImpl.createGdkMethod(method, myStatic, info);
        if (!processor.execute(gdk, state)) {
          return false;
        }
      }
    }

    return true;
  }

  public static GdkMethodHolder getHolderForClass(final PsiClass categoryClass, final boolean isStatic) {
    final Project project = categoryClass.getProject();
    Key<CachedValue<GdkMethodHolder>> key = isStatic ? CACHED_STATIC : CACHED_NON_STATIC;
    return CachedValuesManager.getManager(project).getCachedValue(categoryClass, key, () -> {
      GdkMethodHolder result = new GdkMethodHolder(categoryClass, isStatic);

      final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
      final VirtualFile vfile = categoryClass.getContainingFile().getVirtualFile();
      if (vfile != null && rootManager.getFileIndex().isInLibrary(vfile)) {
        return CachedValueProvider.Result.create(result, rootManager);
      }

      return CachedValueProvider.Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, rootManager);
    }, false);
  }

  @Override
  public String toString() {
    return "GDK Method Holder for " + myClassName;
  }
}
