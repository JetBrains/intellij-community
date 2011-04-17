package org.jetbrains.plugins.groovy.util.dynamicMembers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicMemberUtils {

  private static final Key<Map<String, ClassMemberHolder>> KEY = Key.create("DynamicMemberUtils");

  private DynamicMemberUtils() {

  }

  public static ClassMemberHolder getMembers(@NotNull Project project, @NotNull String source) {
    Map<String, ClassMemberHolder> map = project.getUserData(KEY);

    if (map == null) {
      map = new ConcurrentHashMap<String, ClassMemberHolder>();
      map = ((UserDataHolderEx)project).putUserDataIfAbsent(KEY, map);
    }

    ClassMemberHolder res = map.get(source);

    if (res == null) {
      res = new ClassMemberHolder(project, source);
      map.put(source, res);
    }

    assert source == res.myClassSource : "Store class sources in static constant, do not generate it in each call.";
    
    return res;
  }

  public static boolean process(PsiScopeProcessor processor, PsiClass psiClass, GrReferenceExpression ref, String classSource) {
    return process(processor, PsiUtil.isInStaticContext(ref, psiClass), ref, classSource);
  }

  public static boolean process(PsiScopeProcessor processor, boolean isInStaticContext, GroovyPsiElement place, String classSource) {
    ClassHint classHint = processor.getHint(ClassHint.KEY);
    String name = ResolveUtil.getNameHint(processor);

    ClassMemberHolder memberHolder = getMembers(place.getProject(), classSource);

    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) {
      PsiMethod[] methods = isInStaticContext ? memberHolder.getStaticMethods(name) : memberHolder.getMethods(name);
      for (PsiMethod method : methods) {
        if (!processor.execute(method, ResolveState.initial())) return false;
      }
    }

    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) {
      PsiField[] fields = isInStaticContext ? memberHolder.getStaticFields(name) : memberHolder.getFields(name);
      for (PsiField field : fields) {
        if (!processor.execute(field, ResolveState.initial())) return false;
      }
    }

    return true;
  }

  public static class ClassMemberHolder {
    private final String myClassSource;

    private final Map<String, PsiMethod[]> myMethodMap;
    private final Map<String, PsiField[]> myFieldMap;

    private final Map<String, PsiMethod[]> myStaticMethodMap;
    private final Map<String, PsiField[]> myStaticFieldMap;

    private final Map<String, PsiMethod[]> myNonStaticMethodMap;
    private final Map<String, PsiField[]> myNonStaticFieldMap;

    private ClassMemberHolder(Project project, String classSource) {
      myClassSource = classSource;

      final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(project);

      final GrTypeDefinition psiClass = (GrTypeDefinition)elementFactory.createGroovyFile(classSource, false, null).getClasses()[0];

      // Collect fields.
      myFieldMap = new HashMap<String, PsiField[]>();
      myStaticFieldMap = new HashMap<String, PsiField[]>();
      myNonStaticFieldMap = new HashMap<String, PsiField[]>();

      GrField[] fields = psiClass.getFields();

      PsiField[] allFields = new PsiField[fields.length];

      int i = 0;
      for (PsiField field : fields) {
        PsiField dynamicField = new MyGrDynamicPropertyImpl(null, (GrField)field, null, classSource);
        PsiField[] dynamicFieldArray = new PsiField[]{dynamicField};

        if (field.hasModifierProperty(PsiModifier.STATIC)) {
          myStaticFieldMap.put(field.getName(), dynamicFieldArray);
        }
        else {
          myNonStaticFieldMap.put(field.getName(), dynamicFieldArray);
        }

        Object oldValue = myFieldMap.put(field.getName(), dynamicFieldArray);
        assert oldValue == null : "Duplicated field in dynamic class: " + psiClass.getName() + ":" + field.getName();

        allFields[i++] = dynamicField;
      }

      myFieldMap.put(null, allFields);

      // Collect methods..
      checkDuplicatedMethods(psiClass);

      MultiMap<String, PsiMethod> multiMap = new MultiMap<String, PsiMethod>();
      MultiMap<String, PsiMethod> staticMultiMap = new MultiMap<String, PsiMethod>();
      MultiMap<String, PsiMethod> nonStaticMultiMap = new MultiMap<String, PsiMethod>();

      for (GrMethod method : psiClass.getGroovyMethods()) {
        PsiMethod dynamicMethod = new GrDynamicMethodWithCache(method, classSource);

        multiMap.putValue(null, dynamicMethod);
        multiMap.putValue(method.getName(), dynamicMethod);

        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          staticMultiMap.putValue(null, dynamicMethod);
          staticMultiMap.putValue(method.getName(), dynamicMethod);
        }
        else {
          nonStaticMultiMap.putValue(null, dynamicMethod);
          nonStaticMultiMap.putValue(method.getName(), dynamicMethod);
        }
      }

      myMethodMap = convertMap(multiMap);
      myStaticMethodMap = convertMap(staticMultiMap);
      myNonStaticMethodMap = convertMap(nonStaticMultiMap);
    }

    private static void checkDuplicatedMethods(PsiClass psiClass) {
      Set<String> existingMethods = new HashSet<String>();

      for (PsiMethod psiMethod : psiClass.getMethods()) {
        if (!(psiMethod instanceof GrAccessorMethod) && !existingMethods.add(psiMethod.getText())) {
          throw new RuntimeException("Duplicated field in dynamic class: " + psiClass.getName() + ":" + psiMethod.getText());
        }
      }
    }

    private static Map<String, PsiMethod[]> convertMap(MultiMap<String, PsiMethod> multiMap) {
      Map<String, PsiMethod[]> res = new HashMap<String, PsiMethod[]>();

      for (String methodName : multiMap.keySet()) {
        Collection<PsiMethod> m = multiMap.get(methodName);
        res.put(methodName, m.toArray(new PsiMethod[m.size()]));
      }

      return res;
    }

    public PsiMethod[] getMethods() {
      return getMethods(null);
    }

    public PsiMethod[] getDynamicMethods(@Nullable String nameHint) {
      PsiMethod[] res = myNonStaticMethodMap.get(nameHint);
      if (res == null) {
        res = PsiMethod.EMPTY_ARRAY;
      }

      return res;
    }

    public PsiMethod[] getStaticMethods(@Nullable String nameHint) {
      PsiMethod[] res = myStaticMethodMap.get(nameHint);
      if (res == null) {
        res = PsiMethod.EMPTY_ARRAY;
      }

      return res;
    }

    public PsiMethod[] getMethods(@Nullable String nameHint) {
      PsiMethod[] res = myMethodMap.get(nameHint);
      if (res == null) {
        res = PsiMethod.EMPTY_ARRAY;
      }

      return res;
    }

    public PsiField[] getFields() {
      return getFields(null);
    }

    public PsiField[] getFields(@Nullable String nameHint) {
      PsiField[] res = myFieldMap.get(nameHint);
      if (res == null) {
        res = PsiField.EMPTY_ARRAY;
      }

      return res;
    }

    public PsiField[] getStaticFields(@Nullable String nameHint) {
      PsiField[] res = myStaticFieldMap.get(nameHint);
      if (res == null) {
        res = PsiField.EMPTY_ARRAY;
      }

      return res;
    }
  }

  public static boolean isDynamicElement(@Nullable PsiElement element) {
    return element instanceof DynamicElement;
  }

  public static boolean isDynamicElement(@Nullable PsiElement element, @NotNull String classSource) {
    return element instanceof DynamicElement && classSource.equals(((DynamicElement)element).getSource());
  }

  private interface DynamicElement {
    String getSource();
  }

  private static class GrDynamicMethodWithCache extends GrDynamicMethodImpl implements DynamicElement {

    private PsiTypeParameter[] myTypeParameters;
    private GrParameterList myParameterList;
    private String[] namedParameters;

    public final String mySource;

    public GrDynamicMethodWithCache(GrMethod method, String source) {
      super(method);
      myTypeParameters = super.getTypeParameters();
      myParameterList = super.getParameterList();
      namedParameters = super.getNamedParametersArray();
      mySource = source;
    }

    @Override
    public String getText() {
      return myMethod.getText();
    }

    @NotNull
    @Override
    public PsiTypeParameter[] getTypeParameters() {
      return myTypeParameters;
    }

    @NotNull
    @Override
    public GrParameterList getParameterList() {
      return myParameterList;
    }

    @NotNull
    @Override
    public String[] getNamedParametersArray() {
      return namedParameters;
    }

    @Override
    public Icon getIcon(int flags) {
      return myMethod.getIcon(flags);
    }

    @Override
    public String getSource() {
      return mySource;
    }
  }

  private static class MyGrDynamicPropertyImpl extends GrDynamicPropertyImpl implements DynamicElement {
    private final String mySource;

    private MyGrDynamicPropertyImpl(PsiClass containingClass, GrField field, PsiElement navigationalElement, String source) {
      super(containingClass, field, navigationalElement);
      mySource = source;
    }

    @Override
    public String getSource() {
      return mySource;
    }
  }
}
