package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.reference.SoftReference;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentWeakHashMap;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ResolveCache {
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP = Key.create("ResolveCache.JAVA_RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP = Key.create("ResolveCache.RESOLVE_MAP");
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.JAVA_RESOLVE_MAP_INCOMPLETE");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.RESOLVE_MAP_INCOMPLETE");
  private static final Key<List<Thread>> IS_BEING_RESOLVED_KEY = Key.create("ResolveCache.IS_BEING_RESOLVED_KEY");
  private static final Key<MapPair<PsiVariable, Object>> VAR_TO_CONST_VALUE_MAP_KEY = Key.create("ResolveCache.VAR_TO_CONST_VALUE_MAP_KEY");

  //store types for method call expressions, NB: this caching is semantical, without this captured wildcards won't work
  private final ConcurrentWeakHashMap<PsiExpression, WeakReference<PsiType>> myCaclulatedlTypes = new ConcurrentWeakHashMap<PsiExpression, WeakReference<PsiType>>();

  private static final Object NULL = Key.create("NULL");

  private final PsiManagerImpl myManager;

  private final Map<PsiVariable,Object> myVarToConstValueMap1;
  private final Map<PsiVariable,Object> myVarToConstValueMap2;

  private final Map<PsiPolyVariantReference,Reference<ResolveResult[]>>[] myPolyVariantResolveMaps = new Map[4];
  private final Map<PsiReference,Reference<PsiElement>>[] myResolveMaps = new Map[4];
  private final AtomicInteger myClearCount = new AtomicInteger(0);


  public static interface AbstractResolver<Ref,Result> {
    Result resolve(Ref ref, boolean incompleteCode);
  }
  public static interface PolyVariantResolver extends AbstractResolver<PsiPolyVariantReference,ResolveResult[]> {
  }

  public static interface Resolver extends AbstractResolver<PsiReference,PsiElement>{
  }

  public ResolveCache(PsiManagerImpl manager) {
    myManager = manager;

    myVarToConstValueMap1 = getOrCreateWeakMap(myManager, VAR_TO_CONST_VALUE_MAP_KEY, true);
    myVarToConstValueMap2 = getOrCreateWeakMap(myManager, VAR_TO_CONST_VALUE_MAP_KEY, false);

    myPolyVariantResolveMaps[0] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, true);
    myPolyVariantResolveMaps[1] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, true);
    myResolveMaps[0] = getOrCreateWeakMap(myManager, RESOLVE_MAP, true);
    myResolveMaps[1] = getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, true);

    myPolyVariantResolveMaps[2] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, false);
    myPolyVariantResolveMaps[3] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, false);

    myResolveMaps[2] = getOrCreateWeakMap(myManager, RESOLVE_MAP, false);
    myResolveMaps[3] = getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, false);

    myManager.registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        myCaclulatedlTypes.clear();
      }
    });
  }

  public PsiType getType(PsiExpression expr, Function<PsiExpression, PsiType> f) {
    WeakReference<PsiType> ref = myCaclulatedlTypes.get(expr);
    PsiType type = ref == null ? null : ref.get();
    if (type == null) {
      type = f.fun(expr);
      WeakReference<PsiType> existingRef = ConcurrencyUtil.cacheOrGet(myCaclulatedlTypes, expr, new WeakReference<PsiType>(type));
      PsiType existing = existingRef.get();
      if (existing != null) type = existing;
    }
    assert type == null || type.isValid();
    return type;
  }

  public void clearCache() {
    myClearCount.incrementAndGet();
    getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, true).clear();
    getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, true).clear();
    getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, false).clear();
    getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, false).clear();
    getOrCreateWeakMap(myManager, RESOLVE_MAP, true).clear();
    getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, true).clear();
    getOrCreateWeakMap(myManager, RESOLVE_MAP, false).clear();
    getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, false).clear();
  }

  private <Ref extends PsiReference, Result> Result resolve(Ref ref,
                                        AbstractResolver<Ref, Result> resolver,
                                        Map<Ref,Reference<Result>>[] maps,
                                        boolean needToPreventRecursion,
                                        boolean incompleteCode) {
    ProgressManager.getInstance().checkCanceled();

    int clearCountOnStart = myClearCount.intValue();

    boolean physical = ref.getElement().isPhysical();
    Result result = getCached(ref, maps, physical, incompleteCode);
    if (result != null) {
      return result;
    }
     
    if (incompleteCode) {
      result = resolve(ref, resolver, maps, needToPreventRecursion, false);
      if (result != null && !(result instanceof Object[] && ((Object[])result).length == 0)) {
        cache(ref, result, maps, physical, incompleteCode, clearCountOnStart);
        return result;
      }
    }

    if (needToPreventRecursion && !lockElement(ref)) return null;
    try {
      result = resolver.resolve(ref, incompleteCode);
    }
    finally {
      if (needToPreventRecursion) {
        unlockElement(ref);
      }
    }
    cache(ref, result, maps, physical, incompleteCode, clearCountOnStart);
    return result;
  }

  public ResolveResult[] resolveWithCaching(PsiPolyVariantReference ref,
                                            PolyVariantResolver resolver,
                                            boolean needToPreventRecursion,
                                            boolean incompleteCode) {
    ResolveResult[] result = resolve(ref, resolver, myPolyVariantResolveMaps, needToPreventRecursion, incompleteCode);
    return result == null ? JavaResolveResult.EMPTY_ARRAY : result;
  }

  public PsiElement resolveWithCaching(PsiReference ref,
                                       Resolver resolver,
                                       boolean needToPreventRecursion,
                                       boolean incompleteCode) {
    return resolve(ref, resolver, myResolveMaps, needToPreventRecursion, incompleteCode);
  }

  private static boolean lockElement(PsiReference ref) {
    synchronized (IS_BEING_RESOLVED_KEY) {
      PsiElement elt = ref.getElement();

      List<Thread> lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
      final Thread currentThread = Thread.currentThread();
      if (lockingThreads == null) {
        lockingThreads = new ArrayList<Thread>(1);
        elt.putUserData(IS_BEING_RESOLVED_KEY, lockingThreads);
      }
      else {
        if (lockingThreads.contains(currentThread)) return false;
      }
      lockingThreads.add(currentThread);
    }
    return true;
  }

  private static void unlockElement(PsiReference ref) {
    synchronized (IS_BEING_RESOLVED_KEY) {
      PsiElement elt = ref.getElement();

      List<Thread> lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
      if (lockingThreads == null) return;
      final Thread currentThread = Thread.currentThread();
      lockingThreads.remove(currentThread);
      if (lockingThreads.isEmpty()) {
        elt.putUserData(IS_BEING_RESOLVED_KEY, null);
      }
    }
  }

  //for Visual Fabrique
  public void clearResolveCaches(PsiReference ref) {
    myClearCount.incrementAndGet();
    final boolean physical = ref.getElement().isPhysical();
    if (ref instanceof PsiPolyVariantReference) {
      cache((PsiPolyVariantReference)ref, null, myPolyVariantResolveMaps, physical, false, myClearCount.intValue());
      cache((PsiPolyVariantReference)ref, null, myPolyVariantResolveMaps, physical, true, myClearCount.intValue());
    }
  }


  private static int getIndex(boolean physical, boolean ic){
    return (physical ? 0 : 1) << 1 | (ic ? 1 : 0);
  }

  private static <Ref,Result>Result getCached(Ref ref, Map<Ref,Reference<Result>>[] maps, boolean physical, boolean ic){
    int index = getIndex(physical, ic);
    Reference<Result> reference = maps[index].get(ref);
    if(reference == null) return null;
    return reference.get();
  }
  private <Ref,Result> void cache(Ref ref, Result result, Map<Ref,Reference<Result>>[] maps, boolean physical, boolean incompleteCode, final int clearCountOnStart) {
    if (clearCountOnStart != myClearCount.intValue() && result != null) return;

    int index = getIndex(physical, incompleteCode);
    maps[index].put(ref, new SoftReference<Result>(result));
  }

  public static interface ConstValueComputer{
    Object execute(PsiVariable variable, Set<PsiVariable> visitedVars);
  }

  public Object computeConstantValueWithCaching(PsiVariable variable, ConstValueComputer computer, Set<PsiVariable> visitedVars){
    boolean physical = variable.isPhysical();

    Object cached = (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).get(variable);
    if (cached == NULL) return null;
    if (cached != null) return cached;

    Object result = computer.execute(variable, visitedVars);

    (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).put(variable, result != null ? result : NULL);

    return result;
  }

  public <K,V> ConcurrentMap<K,V> getOrCreateWeakMap(final PsiManagerImpl manager, final Key<MapPair<K, V>> key, boolean forPhysical) {
    MapPair<K, V> pair = manager.getUserData(key);
    if (pair == null){
      pair = new MapPair<K,V>();
      pair = manager.putUserDataIfAbsent(key, pair);

      final MapPair<K, V> _pair = pair;
      manager.registerRunnableToRunOnChange(
        new Runnable() {
          public void run() {
            myClearCount.incrementAndGet();
            _pair.physicalMap.clear();
          }
        }
      );
      manager.registerRunnableToRunOnAnyChange(
        new Runnable() {
          public void run() {
            myClearCount.incrementAndGet();
            _pair.nonPhysicalMap.clear();
          }
        }
      );
    }
    return forPhysical ? pair.physicalMap : pair.nonPhysicalMap;
  }

  public static class MapPair<K,V>{
    public final ConcurrentMap<K,V> physicalMap = new ConcurrentWeakHashMap<K, V>();
    public final ConcurrentMap<K,V> nonPhysicalMap = new ConcurrentWeakHashMap<K, V>();
  }
}