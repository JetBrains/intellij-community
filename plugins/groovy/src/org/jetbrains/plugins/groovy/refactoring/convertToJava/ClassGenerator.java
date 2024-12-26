// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
public class ClassGenerator {

  private final ClassNameProvider classNameProvider;
  private final ClassItemGenerator classItemGenerator;

  public ClassGenerator(ClassNameProvider classNameProvider, ClassItemGenerator classItemGenerator) {
    this.classNameProvider = classNameProvider;
    this.classItemGenerator = classItemGenerator;
  }

  private void writePackageStatement(StringBuilder text, GrPackageDefinition packageDefinition) {
    if (packageDefinition != null) {
      ModifierListGenerator.writeModifiers(text, packageDefinition.getAnnotationList(), ModifierListGenerator.JAVA_MODIFIERS,
                                           classItemGenerator.generateAnnotations());
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(';');
      text.append('\n');
      text.append('\n');
    }
  }

  public void writeTypeDefinition(StringBuilder text, final @NotNull PsiClass typeDefinition, boolean toplevel, boolean insertPackageSmst) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    final GroovyFile containingFile = (GroovyFile)typeDefinition.getContainingFile();
    if (insertPackageSmst) {
      writePackageStatement(text, containingFile.getPackageDefinition());
    }

    GenerationUtil.writeDocComment(text, typeDefinition, true);

    boolean isEnum = typeDefinition.isEnum();
    boolean isAnnotationType = typeDefinition.isAnnotationType();
    boolean isInterface = typeDefinition.isInterface();

    ModifierListGenerator.writeClassModifiers(text, typeDefinition.getModifierList(), typeDefinition.isInterface(), typeDefinition.isEnum(), toplevel, classItemGenerator.generateAnnotations());

    if (isAnnotationType) {
      text.append('@');
    }

    if (isInterface) {
      text.append("interface");
    }
    else if (isEnum) {
      text.append("enum");
    }
    else {
      text.append("class");
    }

    text.append(' ').append(typeDefinition.getName());

    GenerationUtil.writeTypeParameters(text, typeDefinition, classNameProvider);

    text.append(' ');

    if (isScript) {
      text.append("extends ").append(GroovyCommonClassNames.GROOVY_LANG_SCRIPT).append(' ');
    }
    else if (!isEnum && !isAnnotationType) {
      classItemGenerator.writeExtendsList(text, typeDefinition);
      classItemGenerator.writeImplementsList(text, typeDefinition);
    }

    text.append("{\n");

    writeMembers(text, typeDefinition);
    text.append('}');
  }

  public void writeMembers(StringBuilder text, PsiClass typeDefinition) {
    if (typeDefinition instanceof GrEnumTypeDefinition) {
      final GrEnumConstant[] enumConstants = ((GrEnumTypeDefinition)typeDefinition).getEnumConstants();
      for (GrEnumConstant constant : enumConstants) {
        classItemGenerator.writeEnumConstant(text, constant);
        text.append(',');
      }
      if (enumConstants.length > 0) {
        //text.removeFromTheEnd(1).append(";\n");
        text.delete(text.length() - 1, text.length());
      }
      text.append(";\n");
    }

    writeAllMethods(text, classItemGenerator.collectMethods(typeDefinition), typeDefinition);

    if (typeDefinition instanceof GrTypeDefinition) {
      for (GrMembersDeclaration declaration : ((GrTypeDefinition)typeDefinition).getMemberDeclarations()) {
        if (declaration instanceof GrVariableDeclaration) {
          classItemGenerator.writeVariableDeclarations(text, (GrVariableDeclaration)declaration);
        }
      }
      for (PsiClass inner : typeDefinition.getInnerClasses()) {
        writeTypeDefinition(text, inner, false, false);
        text.append('\n');
      }
    }

    classItemGenerator.writePostponed(text, typeDefinition);
  }

  private void writeAllMethods(StringBuilder text, Collection<PsiMethod> methods, PsiClass aClass) {
    for (PsiMethod method : methods) {
      if (!shouldBeGenerated(method, aClass)) continue;

      if (method.isConstructor()) {
        classItemGenerator.writeConstructor(text, method, aClass.isEnum());
      }
      else {
        classItemGenerator.writeMethod(text, method);
        text.append('\n');
      }
    }
  }


  private static boolean shouldBeGenerated(PsiMethod method, PsiClass containingClass) {
    if (containingClass instanceof GrEnumTypeDefinitionImpl &&
        ((GrEnumTypeDefinitionImpl)containingClass).isPredefinedEnumMethod(method)) {
      return false;
    }

    for (PsiMethod psiMethod : method.findSuperMethods()) {
      if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final PsiType type = method.getReturnType();
        final PsiType superType = psiMethod.getReturnType();
        if (type != null && superType != null && !superType.isAssignableFrom(type)) {
          return false;
        }
      }
    }
    return true;
  }
}
