package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class CollectClassMembersUtil {
  private static final Key<CachedValue<Pair<Map<String, CandidateInfo>,
                                            Map<String, List<CandidateInfo>>>>> CACHED_MEMBERS = Key.create("CACHED_CLASS_MEMBERS");

  public static Map<String, List<CandidateInfo>> getAllMethods(final PsiClass aClass) {
    CachedValue<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> cachedValue = aClass.getUserData(CACHED_MEMBERS);
    if (cachedValue == null) {
      cachedValue = buildCache(aClass);
    }

    Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>> value = cachedValue.getValue();
    assert value != null;
    return value.getSecond();
  }

  public static Map<String, CandidateInfo> getAllFields(final PsiClass aClass) {
    CachedValue<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> cachedValue = aClass.getUserData(CACHED_MEMBERS);
    if (cachedValue == null) {
      cachedValue = buildCache(aClass);
    }

    Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>> value = cachedValue.getValue();
    assert value != null;
    return value.getFirst();
  }

  private static CachedValue<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> buildCache(final PsiClass aClass) {
    return aClass.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>>() {
      public Result<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> compute() {
        Map<String, CandidateInfo> allFields = new HashMap<String, CandidateInfo>();
        Map<String, List<CandidateInfo>> allMethods = new HashMap<String, List<CandidateInfo>>();

        processClass(aClass, allFields, allMethods, new HashSet<PsiClass>(), PsiSubstitutor.EMPTY);
        return new Result<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>>(new Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>(allFields, allMethods), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    }, false);
  }

  private static void processClass(PsiClass aClass, Map<String, CandidateInfo> allFields, Map<String, List<CandidateInfo>> allMethods, Set<PsiClass> visitedClasses, PsiSubstitutor substitutor) {
    if (visitedClasses.contains(aClass)) return;
    visitedClasses.add(aClass);

    for (PsiField field : aClass.getFields()) {
      String name = field.getName();
      if (!allFields.containsKey(name)) {
        allFields.put(name, new CandidateInfo(field, substitutor));
      }
      if (field instanceof GrField && field.getName() != null) {
        final GrField property = (GrField) field;
        final PsiMethod getter = property.getGetter();
        if (getter != null) addMethod(allMethods, getter, substitutor);
        final PsiMethod setter = property.getSetter();
        if (setter != null) addMethod(allMethods, setter, substitutor);
      }
    }

    for (PsiMethod method : aClass.getMethods()) {
      addMethod(allMethods, method, substitutor);
    }

    String qName = aClass.getQualifiedName();
    if (qName != null) {
      List<PsiMethod> defaultMethods = GroovyPsiManager.getInstance(aClass.getProject()).getDefaultMethods(qName);
      for (PsiMethod defaultMethod : defaultMethods) {
        addMethod(allMethods, defaultMethod, substitutor);
      }
    }

    for (PsiClassType superType : aClass.getSuperTypes()) {
      PsiClass superClass = superType.resolve();
      if (superClass != null) {
        final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, substitutor);
        processClass(superClass, allFields, allMethods, visitedClasses, superSubstitutor);
      }
    }
  }

  private static void addMethod(Map<String, List<CandidateInfo>> allMethods, PsiMethod method, PsiSubstitutor substitutor) {
    String name = method.getName();
    List<CandidateInfo> methods = allMethods.get(name);
    if (methods == null) {
      methods = new ArrayList<CandidateInfo>();
      allMethods.put(name, methods);
      methods.add(new CandidateInfo(method, substitutor));
    } else if (!isSuperMethodDominated(method, methods)) methods.add(new CandidateInfo(method, substitutor));
  }

  private static boolean isSuperMethodDominated(PsiMethod method, List<CandidateInfo> worklist) {
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiModifierList modifierList = method.getModifierList();

    NextMethod:
    for (CandidateInfo info : worklist) {
      PsiMethod other = (PsiMethod) info.getElement();
      assert other != null;
      PsiParameter[] otherParams = other.getParameterList().getParameters();
      if (otherParams.length != params.length) continue;
      if (PsiUtil.getAccessLevel(other.getModifierList()) > PsiUtil.getAccessLevel(modifierList)) continue;
      for (int i = 0; i < params.length; i++) {
        PsiType type = TypeConversionUtil.erasure(params[i].getType());
        PsiType otherType = TypeConversionUtil.erasure(otherParams[i].getType());
        if (!type.isAssignableFrom(otherType)) continue NextMethod;
      }
      return true;
    }

    return false;
  }
}
