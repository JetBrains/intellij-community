/*
 * Copyright 2003-2015 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class SerialVersionUIDBuilder extends JavaRecursiveElementVisitor {

  @NonNls private static final String ACCESS_METHOD_NAME_PREFIX = "access$";

  private final PsiClass clazz;
  private int index = -1;
  private final Set<MemberSignature> nonPrivateConstructors;
  private final Set<MemberSignature> nonPrivateMethods;
  private final Set<MemberSignature> nonPrivateFields;
  private final List<MemberSignature> staticInitializers;
  private boolean assertStatement = false;
  private final Map<PsiElement, String> memberMap = new HashMap<>();

  private static final Comparator<PsiClass> INTERFACE_COMPARATOR =
    (object1, object2) -> {
      if (object1 == null && object2 == null) {
        return 0;
      }
      if (object1 == null) {
        return 1;
      }
      if (object2 == null) {
        return -1;
      }
      final String name1 = object1.getQualifiedName();
      final String name2 = object2.getQualifiedName();
      if (name1 == null && name2 == null) {
        return 0;
      }
      if (name1 == null) {
        return 1;
      }
      if (name2 == null) {
        return -1;
      }
      return name1.compareTo(name2);
    };

  private SerialVersionUIDBuilder(PsiClass clazz) {
    this.clazz = clazz;
    nonPrivateMethods = new HashSet<>();
    final PsiMethod[] methods = clazz.getMethods();
    for (final PsiMethod method : methods) {
      if (!method.isConstructor() && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        final MemberSignature methodSignature = new MemberSignature(method);
        nonPrivateMethods.add(methodSignature);
        SuperMethodsSearch.search(method, null, true, false).forEach(method1 -> {
          final MemberSignature superSignature = new MemberSignature(methodSignature.getName(), methodSignature.getModifiers(),
                                                                     MemberSignature.createMethodSignature(method1.getMethod()));
          nonPrivateMethods.add(superSignature);
          return true;
        });
      }
    }
    nonPrivateFields = new HashSet<>();
    final PsiField[] fields = clazz.getFields();
    for (final PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.PRIVATE) ||
          !(field.hasModifierProperty(PsiModifier.STATIC) ||
            field.hasModifierProperty(PsiModifier.TRANSIENT))) {
        final MemberSignature fieldSignature =
          new MemberSignature(field);
        nonPrivateFields.add(fieldSignature);
      }
    }

    staticInitializers = new ArrayList<>(1);
    final PsiClassInitializer[] initializers = clazz.getInitializers();
    if (initializers.length > 0) {
      for (final PsiClassInitializer initializer : initializers) {
        final PsiModifierList modifierList =
          initializer.getModifierList();
        if (modifierList != null &&
            modifierList.hasModifierProperty(PsiModifier.STATIC)) {
          final MemberSignature initializerSignature =
            MemberSignature.getStaticInitializerMemberSignature();
          staticInitializers.add(initializerSignature);
          break;
        }
      }
    }
    if (staticInitializers.isEmpty()) {
      final PsiField[] psiFields = clazz.getFields();
      for (final PsiField field : psiFields) {
        if (hasStaticInitializer(field)) {
          final MemberSignature initializerSignature =
            MemberSignature.getStaticInitializerMemberSignature();
          staticInitializers.add(initializerSignature);
          break;
        }
      }
    }

    final PsiMethod[] constructors = clazz.getConstructors();
    nonPrivateConstructors = new HashSet<>(constructors.length);
    if (constructors.length == 0 && !clazz.isInterface()) {
      // generated empty constructor if no constructor is defined in the source
      final MemberSignature constructorSignature;
      if (clazz.hasModifierProperty(PsiModifier.PUBLIC)) {
        constructorSignature = MemberSignature.getPublicConstructor();
      }
      else {
        constructorSignature = MemberSignature.getPackagePrivateConstructor();
      }
      nonPrivateConstructors.add(constructorSignature);
    }
    for (final PsiMethod constructor : constructors) {
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
        final MemberSignature constructorSignature =
          new MemberSignature(constructor);
        nonPrivateConstructors.add(constructorSignature);
      }
    }
  }

  /**
   * @see java.io.ObjectStreamClass#computeDefaultSUID(java.lang.Class)
   */
  public static long computeDefaultSUID(PsiClass psiClass) {
    final Project project = psiClass.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass serializable =
      psiFacade.findClass(CommonClassNames.JAVA_IO_SERIALIZABLE, scope);
    if (serializable == null) {
      // no jdk defined for project.
      return -1L;
    }

    final boolean isSerializable = psiClass.isInheritor(serializable, true);
    if (!isSerializable) {
      return 0L;
    }

    final SerialVersionUIDBuilder serialVersionUIDBuilder = new SerialVersionUIDBuilder(psiClass);
    try {
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

      final String className = PsiFormatUtil.getExternalName(psiClass);
      dataOutputStream.writeUTF(className);

      final PsiModifierList classModifierList = psiClass.getModifierList();
      int classModifiers = classModifierList != null ? MemberSignature.calculateModifierBitmap(classModifierList) : 0;
      final MemberSignature[] methodSignatures = serialVersionUIDBuilder.getNonPrivateMethodSignatures();
      if (psiClass.isInterface()) {
        classModifiers |= Modifier.INTERFACE;
        if (methodSignatures.length == 0) {
          // interfaces were not marked abstract when they did't have methods in java 1.0
          // For serialization compatibility the abstract modifier is ignored.
          classModifiers &= ~Modifier.ABSTRACT;
        }
      }
      dataOutputStream.writeInt(classModifiers);

      final PsiClass[] interfaces = psiClass.getInterfaces();
      Arrays.sort(interfaces, INTERFACE_COMPARATOR);
      for (PsiClass aInterfaces : interfaces) {
        final String name = aInterfaces.getQualifiedName();
        dataOutputStream.writeUTF(name);
      }

      final MemberSignature[] fields = serialVersionUIDBuilder.getNonPrivateFields();
      writeSignatures(fields, dataOutputStream);

      final MemberSignature[] staticInitializers = serialVersionUIDBuilder.getStaticInitializers();
      writeSignatures(staticInitializers, dataOutputStream);

      final MemberSignature[] constructors = serialVersionUIDBuilder.getNonPrivateConstructors();
      writeSignatures(constructors, dataOutputStream);

      writeSignatures(methodSignatures, dataOutputStream);

      dataOutputStream.flush();
      @NonNls final String algorithm = "SHA";
      final MessageDigest digest = MessageDigest.getInstance(algorithm);
      final byte[] digestBytes = digest.digest(byteArrayOutputStream.toByteArray());
      long serialVersionUID = 0L;
      for (int i = Math.min(digestBytes.length, 8) - 1; i >= 0; i--) {
        serialVersionUID = serialVersionUID << 8 | digestBytes[i] & 0xFF;
      }
      return serialVersionUID;
    }
    catch (IOException exception) {
      final InternalError internalError = new InternalError(exception.getMessage());
      internalError.initCause(exception);
      throw internalError;
    }
    catch (NoSuchAlgorithmException exception) {
      final SecurityException securityException = new SecurityException(exception.getMessage());
      securityException.initCause(exception);
      throw securityException;
    }
  }

  private static void writeSignatures(MemberSignature[] signatures, DataOutputStream dataOutputStream) throws IOException {
    Arrays.sort(signatures);
    for (final MemberSignature field : signatures) {
      dataOutputStream.writeUTF(field.getName());
      dataOutputStream.writeInt(field.getModifiers());
      dataOutputStream.writeUTF(field.getSignature());
    }
  }

  private String getAccessMethodIndex(PsiElement element) {
    String cache = memberMap.get(element);
    if (cache == null) {
      cache = String.valueOf(index);
      index++;
      memberMap.put(element, cache);
    }
    return cache;
  }

  public MemberSignature[] getNonPrivateConstructors() {
    init();
    return nonPrivateConstructors.toArray(new MemberSignature[nonPrivateConstructors.size()]);
  }

  public MemberSignature[] getNonPrivateFields() {
    init();
    return nonPrivateFields.toArray(new MemberSignature[nonPrivateFields.size()]);
  }

  public MemberSignature[] getNonPrivateMethodSignatures() {
    init();
    return nonPrivateMethods.toArray(new MemberSignature[nonPrivateMethods.size()]);
  }

  public MemberSignature[] getStaticInitializers() {
    init();
    return staticInitializers.toArray(new MemberSignature[staticInitializers.size()]);
  }

  private static boolean hasStaticInitializer(PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiExpression initializer = field.getInitializer();
      if (initializer == null) {
        return false;
      }
      final PsiType fieldType = field.getType();
      final PsiType stringType = TypeUtils.getStringType(field);
      if (field.hasModifierProperty(PsiModifier.FINAL) && (fieldType instanceof PsiPrimitiveType || fieldType.equals(stringType))) {
        return !PsiUtil.isConstantExpression(initializer);
      }
      else {
        return true;
      }
    }
    return false;
  }

  private void init() {
    if (index < 0) {
      index = 0;
      clazz.acceptChildren(this);
    }
  }

  @Override
  public void visitAssertStatement(PsiAssertStatement statement) {
    super.visitAssertStatement(statement);
    if (assertStatement) {
      return;
    }
    final MemberSignature memberSignature =
      MemberSignature.getAssertionsDisabledFieldMemberSignature();
    nonPrivateFields.add(memberSignature);
    if (staticInitializers.isEmpty()) {
      final MemberSignature initializerSignature =
        MemberSignature.getStaticInitializerMemberSignature();
      staticInitializers.add(initializerSignature);
    }
    assertStatement = true;
  }

  @Override
  public void visitMethodCallExpression(
    @NotNull PsiMethodCallExpression methodCallExpression) {
    // for navigating the psi tree in the order javac navigates its AST
    final PsiExpressionList argumentList =
      methodCallExpression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    for (final PsiExpression expression : expressions) {
      expression.accept(this);
    }
    final PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    methodExpression.accept(this);
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);
    final PsiElement parentClass = ClassUtils.getContainingClass(reference);
    if (reference.getParent() instanceof PsiTypeElement) {
      return;
    }
    final PsiElement element = reference.resolve();
    if (!(element instanceof PsiClass)) {
      return;
    }
    final PsiClass elementParentClass =
      ClassUtils.getContainingClass(element);
    if (elementParentClass == null ||
        !elementParentClass.equals(clazz) ||
        element.equals(parentClass)) {
      return;
    }
    final PsiClass innerClass = (PsiClass)element;
    if (!innerClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      return;
    }
    final PsiMethod[] constructors = innerClass.getConstructors();
    if (constructors.length == 0) {
      getAccessMethodIndex(innerClass);
    }
  }

  @Override
  public void visitReferenceExpression(
    @NotNull PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    final PsiElement element = expression.resolve();
    final PsiElement elementParentClass =
      ClassUtils.getContainingClass(element);
    final PsiElement expressionParentClass =
      ClassUtils.getContainingClass(expression);
    if (expressionParentClass == null || expressionParentClass
      .equals(elementParentClass)) {
      return;
    }
    PsiElement parentOfParentClass =
      ClassUtils.getContainingClass(expressionParentClass);
    while (parentOfParentClass != null &&
           !parentOfParentClass.equals(clazz)) {
      if (!(expressionParentClass instanceof PsiAnonymousClass)) {
        getAccessMethodIndex(expressionParentClass);
      }
      getAccessMethodIndex(parentOfParentClass);
      parentOfParentClass = ClassUtils.getContainingClass(parentOfParentClass);
    }
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        boolean isStatic = false;
        final PsiType type = field.getType();
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
          if (field.hasModifierProperty(PsiModifier.FINAL) &&
              type instanceof PsiPrimitiveType) {
            final PsiExpression initializer = field.getInitializer();
            if (PsiUtil.isConstantExpression(initializer)) {
              return;
            }
          }
          isStatic = true;
        }
        final String returnTypeSignature =
          MemberSignature.createTypeSignature(type).replace('/',
                                                            '.');
        final String className = clazz.getQualifiedName();
        @NonNls final StringBuilder signatureBuffer =
          new StringBuilder("(");
        if (!isStatic) {
          signatureBuffer.append('L').append(className).append(';');
        }
        final String accessMethodIndex = getAccessMethodIndex(field);
        if (!field.getContainingClass().equals(clazz)) {
          return;
        }
        @NonNls String name = null;
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
          if (assignment.getLExpression().equals(expression)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "02";
            signatureBuffer.append(returnTypeSignature);
          }
        }
        else if (parent instanceof PsiPostfixExpression) {
          final PsiPostfixExpression postfixExpression =
            (PsiPostfixExpression)parent;
          final IElementType tokenType = postfixExpression.getOperationTokenType();
          if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "08";
          }
          else if (tokenType.equals(JavaTokenType.MINUSMINUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "10";
          }
        }
        else if (parent instanceof PsiPrefixExpression) {
          final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
          final IElementType tokenType = prefixExpression.getOperationTokenType();
          if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "04";
          }
          else if (tokenType.equals(JavaTokenType.MINUSMINUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "06";
          }
        }
        if (name == null) {
          name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "00";
        }
        signatureBuffer.append(')').append(returnTypeSignature);
        final String signature = signatureBuffer.toString();
        final MemberSignature methodSignature =
          new MemberSignature(name, Modifier.STATIC, signature);
        nonPrivateMethods.add(methodSignature);
      }
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.hasModifierProperty(PsiModifier.PRIVATE) &&
          method.getContainingClass().equals(clazz)) {
        final String signature;
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          signature =
            MemberSignature.createMethodSignature(method)
              .replace('/', '.');
        }
        else {
          final String returnTypeSignature =
            MemberSignature.createTypeSignature(method.getReturnType())
              .replace('/', '.');
          @NonNls final StringBuilder signatureBuffer =
            new StringBuilder();
          signatureBuffer.append("(L");
          signatureBuffer.append(clazz.getQualifiedName())
            .append(';');
          final PsiParameter[] parameters = method.getParameterList()
            .getParameters();
          for (final PsiParameter parameter : parameters) {
            final PsiType type = parameter.getType();
            final String typeSignature = MemberSignature.createTypeSignature(type)
              .replace('/', '.');
            signatureBuffer.append(typeSignature);
          }
          signatureBuffer.append(')');
          signatureBuffer.append(returnTypeSignature);
          signature = signatureBuffer.toString();
        }
        final String accessMethodIndex = getAccessMethodIndex(method);
        final MemberSignature methodSignature =
          new MemberSignature(ACCESS_METHOD_NAME_PREFIX +
                              accessMethodIndex + "00",
                              Modifier.STATIC, signature);
        nonPrivateMethods.add(methodSignature);
      }
    }
  }
}
