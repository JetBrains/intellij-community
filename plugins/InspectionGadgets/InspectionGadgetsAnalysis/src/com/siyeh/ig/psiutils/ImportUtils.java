/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ImportUtils {

  private ImportUtils() {}

  public static void addImportIfNeeded(@NotNull PsiClass aClass, @NotNull PsiElement context) {
    final PsiFile file = context.getContainingFile();
    if (!(file instanceof PsiJavaFile)) {
      return;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    final PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) {
      if (PsiTreeUtil.isAncestor(javaFile, aClass, true)) {
        return;
      }
    }
    else if (PsiTreeUtil.isAncestor(outerClass, context, true)) {
      final PsiElement brace = outerClass.getLBrace();
      if (brace != null && brace.getTextOffset() < context.getTextOffset()) {
        return;
      }
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return;
    }
    final String containingPackageName = javaFile.getPackageName();
    @NonNls final String packageName = ClassUtil.extractPackageName(qualifiedName);
    if (CommonClassNames.DEFAULT_PACKAGE.equals(packageName)) {
      return;
    }
    if (containingPackageName.equals(packageName) || importList.findSingleClassImportStatement(qualifiedName) != null) {
      return;
    }
    if (importList.findOnDemandImportStatement(packageName) != null && !hasOnDemandImportConflict(qualifiedName, javaFile)) {
      return;
    }
    if (hasExactImportConflict(qualifiedName, javaFile)) {
      return;
    }
    final Project project = importList.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory elementFactory = psiFacade.getElementFactory();
    final PsiImportStatement importStatement = elementFactory.createImportStatement(aClass);
    importList.add(importStatement);
  }

  private static boolean hasAccessibleMemberWithName(PsiClass containingClass, @NotNull String memberName, @NotNull PsiElement context) {
    final PsiField field = containingClass.findFieldByName(memberName, true);
    if (field != null && PsiUtil.isAccessible(field, context, null)) {
      return true;
    }
    final PsiMethod[] methods = containingClass.findMethodsByName(memberName, true);
    for (PsiMethod method : methods) {
      if (PsiUtil.isAccessible(method, context, null)) {
        return true;
      }
    }
    final PsiClass innerClass = containingClass.findInnerClassByName(memberName, true);
    return innerClass != null && PsiUtil.isAccessible(innerClass, context, null);
  }

  public static boolean nameCanBeImported(@NotNull String fqName, @NotNull PsiElement context) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (containingClass != null) {
      if (fqName.equals(containingClass.getQualifiedName())) {
        return true;
      }
      final String shortName = ClassUtil.extractClassName(fqName);
      final PsiClass[] innerClasses = containingClass.getAllInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (innerClass.hasModifierProperty(PsiModifier.PRIVATE)) {
          continue;
        }
        if (innerClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
          if (!ClassUtils.inSamePackage(innerClass, containingClass)) {
            continue;
          }
        }
        final String className = innerClass.getName();
        if (shortName.equals(className)) {
          return false;
        }
      }
      PsiField field = containingClass.findFieldByName(shortName, false);
      if (field != null) {
        return false;
      }
      field = containingClass.findFieldByName(shortName, true);
      if (field != null && PsiUtil.isAccessible(field, containingClass, null)) {
        return false;
      }
    }
    final PsiJavaFile file = PsiTreeUtil.getParentOfType(context, PsiJavaFile.class);
    if (file == null) {
      return false;
    }
    if (hasExactImportConflict(fqName, file)) {
      return false;
    }
    if (hasOnDemandImportConflict(fqName, file, true)) {
      return false;
    }
    if (containsConflictingReference(file, fqName)) {
      return false;
    }
    if (containsConflictingClass(fqName, file)) {
      return false;
    }
    return !containsConflictingClassName(fqName, file);
  }

  private static boolean containsConflictingClassName(String fqName, PsiJavaFile file) {
    final int lastDotIndex = fqName.lastIndexOf((int)'.');
    final String shortName = fqName.substring(lastDotIndex + 1);
    final PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (shortName.equals(aClass.getName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasExactImportConflict(String fqName, PsiJavaFile file) {
    final PsiImportList imports = file.getImportList();
    if (imports == null) {
      return false;
    }
    final PsiImportStatement[] importStatements = imports.getImportStatements();
    final int lastDotIndex = fqName.lastIndexOf((int)'.');
    final String shortName = fqName.substring(lastDotIndex + 1);
    final String dottedShortName = '.' + shortName;
    for (final PsiImportStatement importStatement : importStatements) {
      if (importStatement.isOnDemand()) {
        continue;
      }
      final String importName = importStatement.getQualifiedName();
      if (importName == null) {
        return false;
      }
      if (!importName.equals(fqName) && importName.endsWith(dottedShortName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasOnDemandImportConflict(@NotNull String fqName, @NotNull PsiElement context) {
    return hasOnDemandImportConflict(fqName, context, false);
  }

  /**
   * @param strict if strict is true this method checks if the conflicting
   *               class which is imported is actually used in the file. If it isn't the
   *               on demand import can be overridden with an exact import for the fqName
   *               without breaking stuff.
   */
  private static boolean hasOnDemandImportConflict(@NotNull String fqName, @NotNull PsiElement context, boolean strict) {
    final PsiFile containingFile = context.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      return false;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
    final PsiImportList imports = javaFile.getImportList();
    if (imports == null) {
      return false;
    }
    final PsiImportStatementBase[] importStatements = imports.getAllImportStatements();
    final String shortName = ClassUtil.extractClassName(fqName);
    final String packageName = ClassUtil.extractPackageName(fqName);
    for (final PsiImportStatementBase importStatement : importStatements) {
      if (!importStatement.isOnDemand()) {
        continue;
      }
      final PsiJavaCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null) {
        continue;
      }
      final String packageText = importReference.getText();
      if (packageText.equals(packageName)) {
        continue;
      }
      final PsiElement element = importReference.resolve();
      if (element instanceof PsiPackage) {
        final PsiPackage aPackage = (PsiPackage)element;
        if (!strict) {
          if (aPackage.findClassByShortName(shortName, containingFile.getResolveScope()).length > 0) {
            return true;
          }
        }
        else {
          final PsiClass[] classes = aPackage.findClassByShortName(shortName, containingFile.getResolveScope());
          for (final PsiClass aClass : classes) {
            final String qualifiedClassName = aClass.getQualifiedName();
            if (qualifiedClassName == null || fqName.equals(qualifiedClassName)) {
              continue;
            }
            return containsConflictingReference(containingFile, qualifiedClassName);
          }
        }
      }
      else if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;
        final PsiClass innerClass = aClass.findInnerClassByName(shortName, true);
        if (importStatement instanceof PsiImportStatement) {
          if (innerClass != null && PsiUtil.isAccessible(innerClass, containingFile, null)) {
            final String qualifiedName = innerClass.getQualifiedName();
            if (!fqName.equals(qualifiedName) && (!strict || containsConflictingReference(containingFile, qualifiedName))) {
              return true;
            }
          }
        }
        else {
          if (innerClass != null && PsiUtil.isAccessible(innerClass, containingFile, null) &&
              innerClass.hasModifierProperty(PsiModifier.STATIC)) {
            final String qualifiedName = innerClass.getQualifiedName();
            if (!fqName.equals(qualifiedName) && (!strict || memberReferenced(innerClass, javaFile))) {
              return true;
            }
          }
          final PsiField field = aClass.findFieldByName(shortName, true);
          if (field != null && PsiUtil.isAccessible(field, containingFile, null) && field.hasModifierProperty(PsiModifier.STATIC)) {
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
              continue;
            }
            final String qualifiedName = containingClass.getQualifiedName() + '.' + field.getName();
            if (!fqName.equals(qualifiedName) && (!strict || memberReferenced(field, javaFile))) {
              return true;
            }
          }
          final PsiMethod[] methods = aClass.findMethodsByName(shortName, true);
          for (PsiMethod method : methods) {
            if (!PsiUtil.isAccessible(method, containingFile, null) || !method.hasModifierProperty(PsiModifier.STATIC)) {
              continue;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
              continue;
            }
            final String qualifiedName = containingClass.getQualifiedName() + '.' + method.getName();
            if (!fqName.equals(qualifiedName) && (!strict || memberReferenced(method, javaFile))) {
              return true;
            }
          }
        }
      }
    }
    return hasJavaLangImportConflict(fqName, javaFile) || hasDefaultImportConflict(fqName, javaFile);
  }

  private static boolean hasDefaultImportConflict(String fqName, PsiJavaFile file) {
    final String shortName = ClassUtil.extractClassName(fqName);
    final String packageName = ClassUtil.extractPackageName(fqName);
    final String filePackageName = file.getPackageName();
    if (filePackageName.equals(packageName)) {
      return false;
    }
    final Project project = file.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiPackage filePackage = psiFacade.findPackage(filePackageName);
    if (filePackage == null) {
      return false;
    }
    return filePackage.containsClassNamed(shortName);
  }

  private static boolean hasJavaLangImportConflict(String fqName, PsiJavaFile file) {
    final String shortName = ClassUtil.extractClassName(fqName);
    final String packageName = ClassUtil.extractPackageName(fqName);
    if (HardcodedMethodConstants.JAVA_LANG.equals(packageName)) {
      return false;
    }
    final Project project = file.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiPackage javaLangPackage = psiFacade.findPackage(HardcodedMethodConstants.JAVA_LANG);
    if (javaLangPackage == null) {
      return false;
    }
    return javaLangPackage.containsClassNamed(shortName);
  }

  private static boolean containsConflictingClass(String fqName, PsiJavaFile file) {
    final PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (containsConflictingInnerClass(fqName, aClass)) {
        return true;
      }
    }
    return false;
  }

  /**
   * ImportUtils currently checks all inner classes, even those that are
   * contained in inner classes themselves, because it doesn't know the
   * location of the original fully qualified reference. It should really only
   * check if the containing class of the fully qualified reference has any
   * conflicting inner classes.
   */
  private static boolean containsConflictingInnerClass(String fqName, PsiClass aClass) {
    final String shortName = ClassUtil.extractClassName(fqName);
    if (shortName.equals(aClass.getName()) && !fqName.equals(aClass.getQualifiedName())) {
      return true;
    }
    final PsiClass[] classes = aClass.getInnerClasses();
    for (PsiClass innerClass : classes) {
      if (containsConflictingInnerClass(fqName, innerClass)) {
        return true;
      }
    }
    return false;
  }

  public static boolean addStaticImport(@NotNull String qualifierClass, @NonNls @NotNull String memberName, @NotNull PsiElement context) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (InheritanceUtil.isInheritor(containingClass, qualifierClass)) {
      return true;
    }
    if (hasAccessibleMemberWithName(containingClass, memberName, context)) {
      return false;
    }
    final PsiFile contextFile = context.getContainingFile();
    if (!(contextFile instanceof PsiJavaFile)) {
      return false;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)contextFile;
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final PsiImportStatementBase existingImportStatement = importList.findSingleImportStatement(memberName);
    if (existingImportStatement != null) {
      if (existingImportStatement instanceof PsiImportStaticStatement) {
        final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)existingImportStatement;
        if (!memberName.equals(importStaticStatement.getReferenceName())) {
          return false;
        }
        final PsiClass targetClass = importStaticStatement.resolveTargetClass();
        return targetClass != null && qualifierClass.equals(targetClass.getQualifiedName());
      }
      return false;
    }
    final PsiImportStaticStatement onDemandImportStatement = findOnDemandImportStaticStatement(importList, qualifierClass);
    if (onDemandImportStatement != null && !hasOnDemandImportConflict(qualifierClass + '.' + memberName, javaFile)) {
      return true;
    }
    final Project project = context.getProject();
    final GlobalSearchScope scope = context.getResolveScope();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass aClass = psiFacade.findClass(qualifierClass, scope);
    if (aClass == null || !PsiUtil.isAccessible(aClass, contextFile, null) ||
        !hasAccessibleMemberWithName(aClass, memberName, contextFile)) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }
    final List<PsiImportStaticStatement> imports = getMatchingImports(importList, qualifiedName);
    final int onDemandCount = JavaCodeStyleSettingsFacade.getInstance(project).getNamesCountToUseImportOnDemand();
    final PsiElementFactory elementFactory = psiFacade.getElementFactory();
    if (imports.size() + 1 < onDemandCount) {
      importList.add(elementFactory.createImportStaticStatement(aClass, memberName));
    }
    else {
      for (PsiImportStaticStatement importStatement : imports) {
        importStatement.delete();
      }
      importList.add(elementFactory.createImportStaticStatement(aClass, "*"));
    }
    return true;
  }

  @Nullable
  private static PsiImportStaticStatement findOnDemandImportStaticStatement(PsiImportList importList, String qualifierClass) {
    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
      if (!importStaticStatement.isOnDemand()) {
        continue;
      }
      final PsiJavaCodeReferenceElement importReference = importStaticStatement.getImportReference();
      if (importReference == null) {
        continue;
      }
      final String text = importReference.getText();
      if (qualifierClass.equals(text)) {
        return importStaticStatement;
      }
    }
    return null;
  }

  private static List<PsiImportStaticStatement> getMatchingImports(@NotNull PsiImportList importList, @NotNull String className) {
    final List<PsiImportStaticStatement> imports = new ArrayList<>();
    for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
      final PsiClass psiClass = staticStatement.resolveTargetClass();
      if (psiClass == null) {
        continue;
      }
      if (!className.equals(psiClass.getQualifiedName())) {
        continue;
      }
      imports.add(staticStatement);
    }
    return imports;
  }

  public static boolean isStaticallyImported(@NotNull PsiMember member, @NotNull PsiElement context) {
    final PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return false;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      return false;
    }
    final PsiFile psiFile = context.getContainingFile();
    if (!(psiFile instanceof PsiJavaFile)) {
      return false;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)psiFile;
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final String memberName = member.getName();
    if (memberName == null) {
      return false;
    }
    final PsiImportStatementBase existingImportStatement = importList.findSingleImportStatement(memberName);
    if (existingImportStatement instanceof PsiImportStaticStatement) {
      final PsiClass importClass = ((PsiImportStaticStatement)existingImportStatement).resolveTargetClass();
      if (InheritanceUtil.isInheritorOrSelf(importClass, memberClass, true)) {
        return true;
      }
    }
    final String memberClassName = memberClass.getQualifiedName();
    if (memberClassName == null) {
      return false;
    }
    final PsiImportStaticStatement onDemandImportStatement = findOnDemandImportStaticStatement(importList, memberClassName);
    if (onDemandImportStatement != null) {
      if (!hasOnDemandImportConflict(memberClassName + '.' + memberName, javaFile)) {
        return true;
      }
    }
    return false;
  }

  private static boolean memberReferenced(PsiMember member, PsiElement context) {
    final MemberReferenceVisitor visitor = new MemberReferenceVisitor(member);
    context.accept(visitor);
    return visitor.isReferenceFound();
  }

  private static class MemberReferenceVisitor extends JavaRecursiveElementWalkingVisitor {
    private final PsiMember[] members;
    private boolean referenceFound;

    private MemberReferenceVisitor(PsiMember member) {
      members = new PsiMember[]{member};
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      if (referenceFound) {
        return;
      }
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      for (PsiMember member : members) {
        if (member.equals(target)) {
          referenceFound = true;
          return;
        }
      }
    }

    private boolean isReferenceFound() {
      return referenceFound;
    }
  }

  /**
   * @return true, if the element contains a reference to a different class than fullyQualifiedName but which has the same class name
   */
  private static boolean containsConflictingReference(PsiFile element, String fullyQualifiedName) {
    final Map<String, Boolean> cachedValue =
      CachedValuesManager.getCachedValue(element, () -> new CachedValueProvider.Result<>(Collections.synchronizedMap(
        new HashMap<>()),
                                                                                         PsiModificationTracker.MODIFICATION_COUNT));
    Boolean conflictingRef = cachedValue.get(fullyQualifiedName);
    if (conflictingRef != null) {
      return conflictingRef.booleanValue();
    }

    final ConflictingClassReferenceVisitor visitor = new ConflictingClassReferenceVisitor(fullyQualifiedName);
    element.accept(visitor);
    conflictingRef = visitor.isConflictingReferenceFound();
    cachedValue.put(fullyQualifiedName, conflictingRef);

    return conflictingRef.booleanValue();
  }

  private static class ConflictingClassReferenceVisitor extends JavaRecursiveElementWalkingVisitor {

    private final String name;
    private final String fullyQualifiedName;
    private boolean referenceFound;

    private ConflictingClassReferenceVisitor(String fullyQualifiedName) {
      name = ClassUtil.extractClassName(fullyQualifiedName);
      this.fullyQualifiedName = fullyQualifiedName;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (referenceFound) return;
      super.visitElement(element);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      if (referenceFound) {
        return;
      }
      super.visitReferenceElement(reference);

      if (reference.getQualifier() != null) return;

      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass) || element instanceof PsiTypeParameter) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final String testClassName = aClass.getName();
      final String testClassQualifiedName = aClass.getQualifiedName();
      if (testClassQualifiedName == null || testClassName == null ||
          testClassQualifiedName.equals(fullyQualifiedName) || !testClassName.equals(name)) {
        return;
      }
      referenceFound = true;
    }

    private boolean isConflictingReferenceFound() {
      return referenceFound;
    }
  }
}