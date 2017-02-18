/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.util.dynamicMembers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicMemberUtils {

  public static final Key<Map<String, String>> COMMENT_KEY = Key.create("DynamicMemberUtils:COMMENT_KEY");

  private static final Key<ConcurrentHashMap<String, ClassMemberHolder>> KEY = Key.create("DynamicMemberUtils");

  private DynamicMemberUtils() {

  }

  public static ClassMemberHolder getMembers(@NotNull Project project, @NotNull String source) {
    ConcurrentHashMap<String, ClassMemberHolder> map = project.getUserData(KEY);

    if (map == null) {
      map = new ConcurrentHashMap<>();
      map = ((UserDataHolderEx)project).putUserDataIfAbsent(KEY, map);
    }

    ClassMemberHolder res = map.get(source);

    if (res == null) {
      res = new ClassMemberHolder(project, source);
      ClassMemberHolder oldValue = map.putIfAbsent(source, res);
      if (oldValue != null) {
        res = oldValue;
      }
    }

    assert source == res.myClassSource : "Store class sources in static constant, do not generate it in each call.";

    return res;
  }

  public static boolean process(PsiScopeProcessor processor, PsiClass psiClass, GrReferenceExpression ref, String classSource) {
    return process(processor, GrStaticChecker.isInStaticContext(ref, psiClass), ref, classSource);
  }

  public static boolean process(PsiScopeProcessor processor, boolean isInStaticContext, PsiElement place, String classSource) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    String name = ResolveUtil.getNameHint(processor);

    ClassMemberHolder memberHolder = getMembers(place.getProject(), classSource);

    if (ResolveUtil.shouldProcessMethods(classHint)) {
      PsiMethod[] methods = isInStaticContext ? memberHolder.getStaticMethods(name) : memberHolder.getMethods(name);
      for (PsiMethod method : methods) {
        if (!processor.execute(method, ResolveState.initial())) return false;
      }
    }

    if (ResolveUtil.shouldProcessProperties(classHint)) {
      PsiField[] fields = isInStaticContext ? memberHolder.getStaticFields(name) : memberHolder.getFields(name);
      for (PsiField field : fields) {
        if (!processor.execute(field, ResolveState.initial())) return false;
      }
    }

    return true;
  }

  public static boolean checkVersion(PsiMethod method, String version) {
    String since = getCommentValue(method, "since");
    if (since == null) return true;

    return version.compareTo(since) >= 0;
  }

  @Nullable
  public static String getCommentValue(PsiMethod method, String commentTagName) {
    Map<String, String> commentMap = method.getUserData(COMMENT_KEY);
    if (commentMap == null) return null;
    return commentMap.get(commentTagName);
  }

  public static class ClassMemberHolder {
    private final String myClassSource;

    private final GrTypeDefinition myClass;

    private final Map<String, PsiMethod[]> myMethodMap;
    private final Map<String, PsiField[]> myFieldMap;

    private final Map<String, PsiMethod[]> myStaticMethodMap;
    private final Map<String, PsiField[]> myStaticFieldMap;

    private final Map<String, PsiMethod[]> myNonStaticMethodMap;
    private final Map<String, PsiField[]> myNonStaticFieldMap;

    private ClassMemberHolder(Project project, String classSource) {
      myClassSource = classSource;

      final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(project);

      myClass = (GrTypeDefinition)elementFactory.createGroovyFile(classSource, false, null).getClasses()[0];

      Map<String, String> classCommentMap = parseComment(myClass.getDocComment());

      // Collect fields.
      myFieldMap = new HashMap<>();
      myStaticFieldMap = new HashMap<>();
      myNonStaticFieldMap = new HashMap<>();

      GrField[] fields = myClass.getCodeFields();

      PsiField[] allFields = new PsiField[fields.length];

      int i = 0;
      for (PsiField field : fields) {
        MyGrDynamicPropertyImpl dynamicField = new MyGrDynamicPropertyImpl(myClass, (GrField)field, null, classSource);

        Map<String, String> commentMap = parseComment(((GrField)field).getDocComment());
        String originalInfo = commentMap.get("originalInfo");
        if (originalInfo == null) {
          originalInfo = classCommentMap.get("originalInfo");
        }
        dynamicField.setOriginalInfo(originalInfo);

        PsiField[] dynamicFieldArray = new PsiField[]{dynamicField};

        if (field.hasModifierProperty(PsiModifier.STATIC)) {
          myStaticFieldMap.put(field.getName(), dynamicFieldArray);
        }
        else {
          myNonStaticFieldMap.put(field.getName(), dynamicFieldArray);
        }

        Object oldValue = myFieldMap.put(field.getName(), dynamicFieldArray);
        assert oldValue == null : "Duplicated field in dynamic class: " + myClass.getName() + ":" + field.getName();

        allFields[i++] = dynamicField;
      }

      myFieldMap.put(null, allFields);

      // Collect methods..
      checkDuplicatedMethods(myClass);

      MultiMap<String, PsiMethod> multiMap = new MultiMap<>();
      MultiMap<String, PsiMethod> staticMultiMap = new MultiMap<>();
      MultiMap<String, PsiMethod> nonStaticMultiMap = new MultiMap<>();

      for (GrMethod method : myClass.getCodeMethods()) {
        GrDynamicMethodWithCache dynamicMethod = new GrDynamicMethodWithCache(method, classSource);

        Map<String, String> commentMap = parseComment(method.getDocComment());
        if (!commentMap.isEmpty()) {
          dynamicMethod.putUserData(COMMENT_KEY, commentMap);
        }

        String originalInfo = commentMap.get("originalInfo");
        if (originalInfo == null) {
          originalInfo = classCommentMap.get("originalInfo");
        }
        dynamicMethod.setOriginalInfo(originalInfo);

        String kind = commentMap.get("kind");
        if (kind == null) {
          kind = classCommentMap.get("kind");
        }
        if (kind != null) {
          dynamicMethod.putUserData(GrLightMethodBuilder.KIND_KEY, kind);
        }

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

    private static Map<String, String> parseComment(@Nullable GrDocComment comment) {
      if (comment == null) return Collections.emptyMap();

      GrDocTag[] docTags = comment.getTags();

      if (docTags.length == 0) return Collections.emptyMap();

      Map<String, String> res = new HashMap<>();

      for (GrDocTag tag : docTags) {
        String tagText = tag.getText().trim();

        int idx = tagText.indexOf(' ');
        if (idx != -1) {
          res.put(tag.getName(), tagText.substring(idx + 1).trim());
        }
      }

      return res;
    }

    public GrTypeDefinition getParsedClass() {
      return myClass;
    }

    private static void checkDuplicatedMethods(GrTypeDefinition psiClass) {
      Set<String> existingMethods = new HashSet<>();

      for (PsiMethod psiMethod : psiClass.getCodeMethods()) {
        if (!(psiMethod instanceof GrAccessorMethod) &&
            !(psiMethod instanceof GrReflectedMethod) &&
            !existingMethods.add(psiMethod.getText())) {
          throw new RuntimeException("Duplicated field in dynamic class: " + psiClass.getName() + ":" + psiMethod.getText());
        }
      }
    }

    private static Map<String, PsiMethod[]> convertMap(MultiMap<String, PsiMethod> multiMap) {
      Map<String, PsiMethod[]> res = new HashMap<>();

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

  public interface DynamicElement {
    String getSource();

    PsiClass getSourceClass();
  }

  private static class GrDynamicMethodWithCache extends GrDynamicMethodImpl implements DynamicElement, OriginInfoAwareElement {

    private final PsiTypeParameter[] myTypeParameters;
    private final GrParameterList myParameterList;
    private final Map<String, NamedArgumentDescriptor> namedParameters;
    private String myOriginalInfo;
    public final String mySource;

    public GrDynamicMethodWithCache(GrMethod method, String source) {
      super(method);
      myTypeParameters = super.getTypeParameters();
      myParameterList = super.getParameterList();
      namedParameters = super.getNamedParameters();
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
    public Map<String, NamedArgumentDescriptor> getNamedParameters() {
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

    @Override
    public PsiClass getSourceClass() {
      return myMethod.getContainingClass();
    }

    @Nullable
    @Override
    public String getOriginInfo() {
      return myOriginalInfo;
    }

    public void setOriginalInfo(String originalInfo) {
      myOriginalInfo = originalInfo;
    }
  }

  private static class MyGrDynamicPropertyImpl extends GrDynamicPropertyImpl implements DynamicElement, OriginInfoAwareElement {
    private final String mySource;
    private final PsiClass myClass;

    private String myOriginalInfo;

    private MyGrDynamicPropertyImpl(PsiClass containingClass, GrField field, PsiElement navigationalElement, String source) {
      super(null, field, navigationalElement);
      myClass = containingClass;
      mySource = source;
    }

    @Override
    public String getSource() {
      return mySource;
    }

    @Override
    public PsiClass getSourceClass() {
      return myClass;
    }

    @Nullable
    @Override
    public String getOriginInfo() {
      return myOriginalInfo;
    }

    public void setOriginalInfo(String originalInfo) {
      myOriginalInfo = originalInfo;
    }
  }
}
