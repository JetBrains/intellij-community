package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.AccessorMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class CollectClassMembersUtil {
  private static final Key<CachedValue<Pair<Map<String, PsiField>,
                                            Map<String, List<PsiMethod>>>>> CACHED_MEMBERS = Key.create("CACHED_CLASS_MEMBERS");

  public static Map<String, List<PsiMethod>> getAllMethods(final PsiClass aClass) {
    CachedValue<Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>>> cachedValue = aClass.getUserData(CACHED_MEMBERS);
    if (cachedValue == null) {
      cachedValue = buildCache(aClass);
    }

    Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>> value = cachedValue.getValue();
    assert value != null;
    return value.getSecond();
  }

  public static Map<String, PsiField> getAllFields(final PsiClass aClass) {
    CachedValue<Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>>> cachedValue = aClass.getUserData(CACHED_MEMBERS);
    if (cachedValue == null) {
      cachedValue = buildCache(aClass);
    }

    Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>> value = cachedValue.getValue();
    assert value != null;
    return value.getFirst();
  }

  private static CachedValue<Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>>> buildCache(final PsiClass aClass) {
    return aClass.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>>>() {
      public Result<Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>>> compute() {
        Map<String, PsiField> allFields = new HashMap<String, PsiField>();
        Map<String, List<PsiMethod>> allMethods = new HashMap<String, List<PsiMethod>>();

        processClass(aClass, allFields, allMethods, new HashSet<PsiClass>());
        return new Result<Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>>>(new Pair<Map<String, PsiField>, Map<String, List<PsiMethod>>>(allFields, allMethods), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    }, false);
  }

  private static void processClass(PsiClass aClass, Map<String, PsiField> allFields, Map<String, List<PsiMethod>> allMethods, Set<PsiClass> visitedClasses) {
    if (visitedClasses.contains(aClass)) return;
    visitedClasses.add(aClass);

    for (PsiField field : aClass.getFields()) {
      String name = field.getName();
      if (!allFields.containsKey(name)) {
        allFields.put(name, field);
      }
      if (field instanceof GrField && field.getName() != null) {
        final GrField property = (GrField) field;
        final PsiMethod getter = property.getGetter();
        if (getter != null) addMethod(allMethods, getter);
        final PsiMethod setter = property.getSetter();
        if (setter != null) addMethod(allMethods, setter);
      }
    }

    for (PsiMethod method : aClass.getMethods()) {
      addMethod(allMethods, method);
    }

    String qName = aClass.getQualifiedName();
    if (qName != null) {
      List<PsiMethod> defaultMethods = GroovyPsiManager.getInstance(aClass.getProject()).getDefaultMethods(qName);
      for (PsiMethod defaultMethod : defaultMethods) {
        addMethod(allMethods, defaultMethod);
      }
    }

    for (PsiClassType superType : aClass.getSuperTypes()) {
      PsiClass superClass = superType.resolve();
      if (superClass != null) {
        processClass(superClass, allFields, allMethods, visitedClasses);
      }
    }
  }

  private static void addMethod(Map<String, List<PsiMethod>> allMethods, PsiMethod method) {
    String name = method.getName();
    List<PsiMethod> methods = allMethods.get(name);
    if (methods == null) {
      methods = new ArrayList<PsiMethod>();
      allMethods.put(name, methods);
      methods.add(method);
    } else if (!ResolveUtil.isSuperMethodDominated(method, methods)) methods.add(method);
  }
}
