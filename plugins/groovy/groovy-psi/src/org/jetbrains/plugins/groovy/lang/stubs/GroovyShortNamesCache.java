// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.stubs;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexImpl;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.*;

import java.util.*;

import static com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys.CLASS_SHORT_NAMES;

public final class GroovyShortNamesCache extends PsiShortNamesCache {
  private final Project myProject;
  private volatile TopLevelFQNames myTopLevelFQNames;
  private volatile TopLevelFQNames myTopLevelScriptFQNames;

  public GroovyShortNamesCache(Project project) {
    myProject = project;
  }

  public static GroovyShortNamesCache getGroovyShortNamesCache(Project project) {
    return Objects
      .requireNonNull(ContainerUtil.findInstance(PsiShortNamesCache.EP_NAME.getExtensionList(project), GroovyShortNamesCache.class));
  }

  private @Nullable TopLevelFQNames getTopLevelNames() {
    TopLevelFQNames topLevelFQNames = myTopLevelFQNames;
    StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    long timestamp = stubIndex.getIndexModificationStamp(GrFullClassNameStringIndex.KEY, myProject) +
                     stubIndex.getIndexModificationStamp(GrFullScriptNameStringIndex.KEY, myProject);
    if (topLevelFQNames != null && topLevelFQNames.timestamp == timestamp) {
      return topLevelFQNames.useful ? topLevelFQNames : null;
    }

    TopLevelFQNames classTopLevelFQNames = new TopLevelFQNames(GrFullClassNameStringIndex.KEY, myProject);
    TopLevelFQNames scriptTopLevelFQNames = new TopLevelFQNames(GrFullScriptNameStringIndex.KEY, myProject);
    myTopLevelScriptFQNames = scriptTopLevelFQNames;

    topLevelFQNames = classTopLevelFQNames.merge(scriptTopLevelFQNames);
    myTopLevelFQNames = topLevelFQNames;
    return topLevelFQNames;
  }

  private @Nullable TopLevelFQNames getScriptTopLevelNames() {
    TopLevelFQNames names = ReadAction.compute(() -> getTopLevelNames());
    if (names == null) return null;

    TopLevelFQNames topLevelFQNames = myTopLevelScriptFQNames;
    StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    return (topLevelFQNames != null &&
            topLevelFQNames.useful &&
            topLevelFQNames.timestamp == stubIndex.getIndexModificationStamp(GrFullScriptNameStringIndex.KEY, myProject))
           ? topLevelFQNames : null;
  }

  /**
   * If <code>fqName</code> is smth like
   * <ul>
   *   <li><code>foo.bar.FooBar</code> it returns <code>foo</code>.</li>
   *   <li><code>FooBar</code> it returns an empty string.</li>
   * </ul>
   *
   * @return top level package name if it is available or string itself otherwise.
   */
  private static @NotNull String toTopLevelName(@NotNull String fqName) {
    int index = fqName.indexOf('.');
    return index >= 1 ? fqName.substring(0, index) : "";
  }

  @Override
  public @NotNull PsiClass @NotNull [] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiClass> allClasses = new SmartList<>();
    processClassesWithName(name, Processors.cancelableCollectProcessor(allClasses), scope, null);
    if (allClasses.isEmpty()) return PsiClass.EMPTY_ARRAY;
    return allClasses.toArray(PsiClass.EMPTY_ARRAY);
  }

  public List<PsiClass> getScriptClassesByFQName(final String name, final GlobalSearchScope scope, final boolean srcOnly) {
    TopLevelFQNames names = getScriptTopLevelNames();
    if (names != null) {
      String topLevelName = toTopLevelName(name);
      if (!names.names.contains(topLevelName)) {
        return Collections.emptyList();
      }
    }
    GlobalSearchScope actualScope = srcOnly ? new GrSourceFilterScope(scope) : scope;
    return ContainerUtil.map(
      StubIndex.getElements(GrFullScriptNameStringIndex.KEY, name, myProject, actualScope, GroovyFile.class),
      o -> Objects.requireNonNull(o.getScriptClass()));
  }

  public @NotNull List<PsiClass> getClassesByFQName(String name, GlobalSearchScope scope, boolean inSource) {
    TopLevelFQNames names = ReadAction.compute(() -> getTopLevelNames());
    if (names != null) {
      String topLevelName = toTopLevelName(name);
      if (!names.names.contains(topLevelName)) {
        return Collections.emptyList();
      }
    }
    if (DumbService.getInstance(myProject).isAlternativeResolveEnabled()) {
      return Collections.emptyList();
    }
    GlobalSearchScope actualScope = inSource ? new GrSourceFilterScope(scope) : scope;
    return ReadAction.compute(() -> {
      List<PsiClass> result = new ArrayList<>();
      result.addAll(StubIndex.getElements(GrFullClassNameStringIndex.KEY, name, myProject, actualScope, PsiClass.class));
      result.addAll(getScriptClassesByFQName(name, scope, inSource));
      return result;
    });
  }

  @Override
  public @NotNull String @NotNull [] getAllClassNames() {
    return ArrayUtilRt.toStringArray(StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY, myProject));
  }

  @Override
  public @NotNull PsiMethod @NotNull [] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiMethod> methods = StubIndex.getElements(GrMethodNameIndex.KEY, name, myProject,
                                                                          new GrSourceFilterScope(scope), GrMethod.class);
    final Collection<? extends PsiMethod> annMethods = StubIndex.getElements(GrAnnotationMethodNameIndex.KEY, name, myProject,
                                                                             new GrSourceFilterScope(scope),
                                                                             GrAnnotationMethod.class);
    if (methods.isEmpty() && annMethods.isEmpty()) return PsiMethod.EMPTY_ARRAY;
    return ArrayUtil.mergeCollections(annMethods, methods, PsiMethod.ARRAY_FACTORY);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<? super PsiMethod> processor) {
    return processMethodsWithName(name, processor, scope, null);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    GrSourceFilterScope filterScope = new GrSourceFilterScope(scope);
    return StubIndex.getInstance().processElements(GrMethodNameIndex.KEY, name, myProject, filterScope, filter, GrMethod.class, processor) &&
           StubIndex.getInstance().processElements(GrAnnotationMethodNameIndex.KEY, name, myProject, filterScope, filter,
                                                   GrAnnotationMethod.class, processor);
  }

  @Override
  public @NotNull PsiMethod @NotNull [] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return getMethodsByName(name, scope);
  }

  @Override
  public @NotNull PsiField @NotNull [] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return getFieldsByName(name, scope);
  }

  @Override
  public @NotNull String @NotNull [] getAllMethodNames() {
    Collection<String> keys = new HashSet<>(StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, myProject));
    keys.addAll(StubIndex.getInstance().getAllKeys(GrAnnotationMethodNameIndex.KEY, myProject));
    return ArrayUtilRt.toStringArray(keys);
  }

  @Override
  public @NotNull PsiField @NotNull [] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiField> fields = StubIndex.getElements(GrFieldNameIndex.KEY, name, myProject,
                                                                        new GrSourceFilterScope(scope), GrField.class);
    if (fields.isEmpty()) return PsiField.EMPTY_ARRAY;
    return fields.toArray(PsiField.EMPTY_ARRAY);
  }

  @Override
  public @NotNull String @NotNull [] getAllFieldNames() {
    Collection<String> fields = StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, myProject);
    return ArrayUtilRt.toStringArray(fields);
  }

  @Override
  public boolean processFieldsWithName(@NotNull String name,
                                       @NotNull Processor<? super PsiField> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(GrFieldNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope), filter,
                                                   GrField.class, processor);
  }

  @Override
  public boolean processClassesWithName(@NotNull String name,
                                        @NotNull Processor<? super PsiClass> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    return processClasses(name, processor, scope, filter) &&
           processScriptClasses(name, processor, scope, filter);
  }

  private boolean processClasses(@NotNull String name,
                                 @NotNull Processor<? super PsiClass> processor,
                                 @NotNull GlobalSearchScope scope,
                                 @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(
      CLASS_SHORT_NAMES, name, myProject, new GrSourceFilterScope(scope), filter, PsiClass.class, processor
    );
  }

  private boolean processScriptClasses(@NotNull String name,
                                       @NotNull Processor<? super PsiClass> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    for (GroovyFile file : StubIndex.getElements(GrScriptClassNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope), filter,
                                                 GroovyFile.class)) {
      PsiClass aClass = file.getScriptClass();
      if (aClass != null && !processor.process(aClass)) return true;
    }
    return true;
  }

  private static class TopLevelFQNames {
    final long timestamp;
    final @NotNull Set<String> names;

    final boolean useful;

    private TopLevelFQNames(@NotNull StubIndexKey<String, ?> indexKey, @NotNull Project project) {
      StubIndexImpl stubIndex = (StubIndexImpl) StubIndex.getInstance();
      this.timestamp = stubIndex.getIndexModificationStamp(indexKey, project);

      Set<String> names = new HashSet<>();
      Processor<String> processor = fqName -> {
        ProgressManager.checkCanceled();
        String topLevelName = toTopLevelName(fqName);
        if (names.add(topLevelName)) {
          // TopLevelFQNames cache becomes useless if it gets too big
          if (names.size() > 500) {
            return false;
          }
        }
        return true;
      };

      boolean useful = stubIndex.processAllKeys(indexKey, project, processor);
      this.names = useful ? names : Collections.emptySet();
      this.useful = useful;
    }

    private TopLevelFQNames(long timestamp, @NotNull Set<String> names, boolean useful) {
      this.timestamp = timestamp;
      this.names = names;
      this.useful = useful;
    }

    public TopLevelFQNames merge(TopLevelFQNames other) {
      boolean mergedUseful = useful && other.useful;
      Set<String> set;
      if (mergedUseful) {
        set = new HashSet<>(names);
        set.addAll(other.names);
      } else {
        set = Collections.emptySet();
      }
      return new TopLevelFQNames(timestamp + other.timestamp, set, mergedUseful);
    }
  }
}
