/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassUtils {

  /**
   * @noinspection StaticCollection
   */
  private static final Set<String> immutableTypes = new HashSet<>(19);

  /**
   * @noinspection StaticCollection
   */
  private static final Set<PsiType> primitiveNumericTypes = new HashSet<>(7);

  /**
   * @noinspection StaticCollection
   */
  private static final Set<PsiType> integralTypes = new HashSet<>(5);

  static {
    integralTypes.add(PsiType.LONG);
    integralTypes.add(PsiType.INT);
    integralTypes.add(PsiType.SHORT);
    integralTypes.add(PsiType.CHAR);
    integralTypes.add(PsiType.BYTE);

    primitiveNumericTypes.add(PsiType.BYTE);
    primitiveNumericTypes.add(PsiType.CHAR);
    primitiveNumericTypes.add(PsiType.SHORT);
    primitiveNumericTypes.add(PsiType.INT);
    primitiveNumericTypes.add(PsiType.LONG);
    primitiveNumericTypes.add(PsiType.FLOAT);
    primitiveNumericTypes.add(PsiType.DOUBLE);

    immutableTypes.add(CommonClassNames.JAVA_LANG_BOOLEAN);
    immutableTypes.add(CommonClassNames.JAVA_LANG_CHARACTER);
    immutableTypes.add(CommonClassNames.JAVA_LANG_SHORT);
    immutableTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    immutableTypes.add(CommonClassNames.JAVA_LANG_LONG);
    immutableTypes.add(CommonClassNames.JAVA_LANG_FLOAT);
    immutableTypes.add(CommonClassNames.JAVA_LANG_DOUBLE);
    immutableTypes.add(CommonClassNames.JAVA_LANG_BYTE);
    immutableTypes.add(CommonClassNames.JAVA_LANG_STRING);
    immutableTypes.add("java.awt.Font");
    immutableTypes.add("java.awt.BasicStroke");
    immutableTypes.add("java.awt.Color");
    immutableTypes.add("java.awt.Cursor");
    immutableTypes.add("java.math.BigDecimal");
    immutableTypes.add("java.math.BigInteger");
    immutableTypes.add("java.math.MathContext");
    immutableTypes.add("java.nio.channels.FileLock");
    immutableTypes.add("java.nio.charset.Charset");
    immutableTypes.add("java.io.File");
    immutableTypes.add("java.net.Inet4Address");
    immutableTypes.add("java.net.Inet6Address");
    immutableTypes.add("java.net.InetSocketAddress");
    immutableTypes.add("java.net.URI");
    immutableTypes.add("java.net.URL");
    immutableTypes.add("java.util.Locale");
    immutableTypes.add("java.util.UUID");
    immutableTypes.add("java.util.regex.Pattern");
  }

  private ClassUtils() {}

  @Nullable
  public static PsiClass findClass(@NonNls String fqClassName, PsiElement context) {
    return JavaPsiFacade.getInstance(context.getProject()).findClass(fqClassName, context.getResolveScope());
  }

  @Nullable
  public static PsiClass findObjectClass(PsiElement context) {
    return findClass(CommonClassNames.JAVA_LANG_OBJECT, context);
  }

  public static boolean isPrimitive(PsiType type) {
    return TypeConversionUtil.isPrimitiveAndNotNull(type);
  }

  public static boolean isIntegral(PsiType type) {
    return integralTypes.contains(type);
  }

  public static boolean isImmutable(PsiType type) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
      return true;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass == null) {
      return false;
    }
    if (immutableTypes.contains(aClass.getQualifiedName())) {
      return true;
    }
    return JCiPUtil.isImmutable(aClass);
  }

  public static boolean inSamePackage(@Nullable PsiElement element1,
                                      @Nullable PsiElement element2) {
    if (element1 == null || element2 == null) {
      return false;
    }
    final PsiFile containingFile1 = element1.getContainingFile();
    if (!(containingFile1 instanceof PsiClassOwner)) {
      return false;
    }
    final PsiClassOwner containingJavaFile1 =
      (PsiClassOwner)containingFile1;
    final String packageName1 = containingJavaFile1.getPackageName();
    final PsiFile containingFile2 = element2.getContainingFile();
    if (!(containingFile2 instanceof PsiClassOwner)) {
      return false;
    }
    final PsiClassOwner containingJavaFile2 =
      (PsiClassOwner)containingFile2;
    final String packageName2 = containingJavaFile2.getPackageName();
    return packageName1.equals(packageName2);
  }

  public static boolean isFieldVisible(@NotNull PsiField field, PsiClass fromClass) {
    final PsiClass fieldClass = field.getContainingClass();
    if (fieldClass == null) {
      return false;
    }
    if (fieldClass.equals(fromClass)) {
      return true;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    if (field.hasModifierProperty(PsiModifier.PUBLIC) ||
        field.hasModifierProperty(PsiModifier.PROTECTED)) {
      return true;
    }
    return inSamePackage(fieldClass, fromClass);
  }

  @Contract("null -> false")
  public static boolean isPrimitiveNumericType(@Nullable PsiType type) {
    return primitiveNumericTypes.contains(type);
  }

  public static boolean isInnerClass(PsiClass aClass) {
    final PsiClass parentClass = getContainingClass(aClass);
    return parentClass != null;
  }

  @Nullable
  public static PsiClass getContainingClass(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiClass.class);
  }

  public static PsiClass getOutermostContainingClass(PsiClass aClass) {
    PsiClass outerClass = aClass;
    while (true) {
      final PsiClass containingClass = getContainingClass(outerClass);
      if (containingClass != null) {
        outerClass = containingClass;
      }
      else {
        return outerClass;
      }
    }
  }

  @Nullable
  public static PsiClass getContainingStaticClass(PsiElement element) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false, PsiFile.class);
    while (isNonStaticClass(aClass)) {
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true, PsiFile.class);
    }
    return aClass;
  }

  public static boolean isNonStaticClass(@Nullable PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC) || aClass.isInterface() || aClass.isEnum()) {
      return false;
    }
    if (aClass instanceof PsiAnonymousClass) {
      return true;
    }
    final PsiElement parent = aClass.getParent();
    if (parent == null || parent instanceof PsiFile) {
      return false;
    }
    if (!(parent instanceof PsiClass)) {
      return true;
    }
    final PsiClass parentClass = (PsiClass)parent;
    return !parentClass.isInterface();
  }

  /**
   * Returns "double brace" initialization for given anonymous class.
   *
   * @param aClass anonymous class to extract the "double brace" initializer from
   * @return "double brace" initializer or null if the class does not follow double brace initialization anti-pattern
   */
  @Nullable
  public static PsiClassInitializer getDoubleBraceInitializer(PsiAnonymousClass aClass) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    if (initializers.length != 1) {
      return null;
    }
    final PsiClassInitializer initializer = initializers[0];
    if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
      return null;
    }
    if (aClass.getFields().length != 0 || aClass.getMethods().length != 0 || aClass.getInnerClasses().length != 0) {
      return null;
    }
    if (aClass.getBaseClassReference().resolve() == null) {
      return null;
    }
    return initializer;
  }

  public static boolean isFinalClassWithDefaultEquals(@Nullable PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    if (!aClass.hasModifierProperty(PsiModifier.FINAL) && !hasOnlyPrivateConstructors(aClass)) {
      return false;
    }
    final PsiMethod[] methods = aClass.findMethodsByName("equals", true);
    for (PsiMethod method : methods) {
      if (!MethodUtils.isEquals(method)) {
        continue;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        return false;
      }
    }
    return true;
  }

  public static boolean hasOnlyPrivateConstructors(PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return false;
    }
    for (PsiMethod constructor : constructors) {
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isSingleton(@Nullable PsiClass aClass) {
    if (aClass == null || aClass.isInterface() || aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
      return false;
    }
    if (aClass.isEnum()) {
      if (!ControlFlowUtils.hasChildrenOfTypeCount(aClass, 1, PsiEnumConstant.class)) {
        return false;
      }
      // has at least on accessible instance method
      return Arrays.stream(aClass.getMethods())
        .anyMatch(m -> !m.isConstructor() && !m.hasModifierProperty(PsiModifier.PRIVATE) && !m.hasModifierProperty(PsiModifier.STATIC));
    }
    if (getIfOnlyInvisibleConstructors(aClass).length == 0) {
      return false;
    }
    final PsiField selfInstance = getIfOneStaticSelfInstance(aClass);
    return selfInstance != null && newOnlyAssignsToStaticSelfInstance(getIfOnlyInvisibleConstructors(aClass)[0], selfInstance);
  }

  private static PsiField getIfOneStaticSelfInstance(PsiClass aClass) {
    final Stream<PsiField> fieldStream = Stream.concat(Arrays.stream(aClass.getFields()),
                                                       Arrays.stream(aClass.getInnerClasses())
                                                         .filter(innerClass -> innerClass.hasModifierProperty(PsiModifier.STATIC))
                                                         .flatMap(innerClass -> Arrays.stream(innerClass.getFields())));
    final List<PsiField> fields = fieldStream.filter(field -> resolveToSingletonField(aClass, field)).limit(2).collect(Collectors.toList());
    return fields.size() == 1 ? fields.get(0) : null;
  }

  private static boolean resolveToSingletonField(PsiClass aClass, PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(field.getType());
    return aClass.equals(targetClass);
  }

  @NotNull
  private static PsiMethod[] getIfOnlyInvisibleConstructors(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return PsiMethod.EMPTY_ARRAY;
    }
    for (final PsiMethod constructor : constructors) {
      if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        return PsiMethod.EMPTY_ARRAY;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE) &&
          !constructor.hasModifierProperty(PsiModifier.PROTECTED)) {
        return PsiMethod.EMPTY_ARRAY;
      }
    }
    return constructors;
  }

  private static boolean newOnlyAssignsToStaticSelfInstance(PsiMethod method, final PsiField field) {
    final Query<PsiReference> search = MethodReferencesSearch.search(method, field.getUseScope(), false);
    final NewOnlyAssignedToFieldProcessor processor = new NewOnlyAssignedToFieldProcessor(field);
    search.forEach(processor);
    return processor.isNewOnlyAssignedToField();
  }

  private static class NewOnlyAssignedToFieldProcessor implements Processor<PsiReference> {

    private boolean newOnlyAssignedToField = true;
    private final PsiField field;

    public NewOnlyAssignedToFieldProcessor(PsiField field) {
      this.field = field;
    }

    @Override
    public boolean process(PsiReference reference) {
      final PsiElement element = reference.getElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (field.equals(grandParent)) {
        return true;
      }
      if (!(grandParent instanceof PsiAssignmentExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)grandParent;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (!field.equals(target)) {
        newOnlyAssignedToField = false;
        return false;
      }
      return true;
    }

    public boolean isNewOnlyAssignedToField() {
      return newOnlyAssignedToField;
    }
  }
}