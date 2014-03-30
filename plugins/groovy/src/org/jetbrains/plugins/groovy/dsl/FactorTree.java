package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;

import java.util.Map;

/**
 * @author peter
 */
public class FactorTree extends UserDataHolderBase {
  private static final Key<CachedValue<Map>> GDSL_MEMBER_CACHE = Key.create("GDSL_MEMBER_CACHE");
  private final CachedValueProvider<Map> myProvider;
  private final CachedValue<Map> myTopLevelCache;
  private final GroovyDslExecutor myExecutor;

  public FactorTree(final Project project, GroovyDslExecutor executor) {
    myExecutor = executor;
    myProvider = new CachedValueProvider<Map>() {
      @Nullable
      @Override
      public Result<Map> compute() {
        return new Result<Map>(new ConcurrentHashMap(), PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
      }
    };
    myTopLevelCache = CachedValuesManager.getManager(project).createCachedValue(myProvider, false);
  }

  public void cache(GroovyClassDescriptor descriptor, CustomMembersHolder holder) {
    Map current = null;
    for (Factor factor : descriptor.affectingFactors) {
      Object key;
      switch (factor) {
        case placeElement: key = descriptor.getPlace(); break;
        case placeFile: key = descriptor.getPlaceFile(); break;
        case qualifierType: key = descriptor.getTypeText(); break;
        default: throw new IllegalStateException("Unknown variant: "+ factor);
      }
      if (current == null) {
        if (key instanceof UserDataHolder) {
          final Project project = descriptor.getProject();
          current = CachedValuesManager.getManager(project).getCachedValue((UserDataHolder)key, GDSL_MEMBER_CACHE, myProvider, false);
          continue;
        }

        current = myTopLevelCache.getValue();
      }
      Map next = (Map)current.get(key);
      if (next == null) {
        //noinspection unchecked
        current.put(key, next = new ConcurrentHashMap());
      }
      current = next;
    }

    if (current == null) current = myTopLevelCache.getValue();
    //noinspection unchecked
    current.put(myExecutor, holder);
  }

  @Nullable
  public CustomMembersHolder retrieve(PsiElement place, PsiFile placeFile, String qualifierType) {
    return retrieveImpl(place, placeFile, qualifierType, myTopLevelCache.getValue(), true);

  }

  @Nullable
  private CustomMembersHolder retrieveImpl(@NotNull PsiElement place, @NotNull PsiFile placeFile, @NotNull String qualifierType, @Nullable Map current, boolean topLevel) {
    if (current == null) return null;

    CustomMembersHolder result;

    result = (CustomMembersHolder)current.get(myExecutor);
    if (result != null) return result;

    result = retrieveImpl(place, placeFile, qualifierType, (Map)current.get(qualifierType), false);
    if (result != null) return result;

    result = retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(placeFile, current, topLevel), false);
    if (result != null) return result;

    return retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(place, current, topLevel), false);
  }

  private static Map getFromMapOrUserData(UserDataHolder holder, Map map, boolean fromUserData) {
    if (fromUserData) {
      CachedValue<Map> cache = holder.getUserData(GDSL_MEMBER_CACHE);
      return cache != null && cache.hasUpToDateValue() ? cache.getValue() : null;
    }
    return (Map)map.get(holder);
  }
}

enum Factor {
  qualifierType, placeElement, placeFile
}
