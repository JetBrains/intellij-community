/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.*;

import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.writeType;

/**
 * @author Maxim.Medvedev
 */
public class ClassGenerator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ClassGenerator");

  private Project myProject;
  private ClassNameProvider classNameProvider;
  private ClassItemGenerator classItemGenerator;

  public ClassGenerator(Project project, ClassNameProvider classNameProvider, ClassItemGenerator classItemGenerator) {
    myProject = project;
    this.classNameProvider = classNameProvider;
    this.classItemGenerator = classItemGenerator;
  }

  private static void writePackageStatement(StringBuilder text, GrPackageDefinition packageDefinition) {
    if (packageDefinition != null) {
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(";");
      text.append("\n");
      text.append("\n");
    }
  }

  public void writeTypeDefinition(StringBuilder text, @NotNull final PsiClass typeDefinition, boolean toplevel) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    final GroovyFile containingFile = (GroovyFile)typeDefinition.getContainingFile();
    if (toplevel) {
      writePackageStatement(text, containingFile.getPackageDefinition());
    }

    boolean isEnum = typeDefinition.isEnum();
    boolean isAnnotationType = typeDefinition.isAnnotationType();
    boolean isInterface = !isAnnotationType && typeDefinition.isInterface();
    boolean isClassDef = !isInterface && !isEnum && !isAnnotationType && !isScript;

    GenerationUtil.writeClassModifiers(text, typeDefinition.getModifierList(), typeDefinition.isInterface(), toplevel);

    if (isInterface) {
      text.append("interface");
    }
    else if (isEnum) {
      text.append("enum");
    }
    else if (isAnnotationType) {
      text.append("@interface");
    }
    else {
      text.append("class");
    }

    text.append(" ").append(typeDefinition.getName());

    GenerationUtil.writeTypeParameters(text, typeDefinition, classNameProvider);

    text.append(" ");

    if (isScript) {
      text.append("extends ").append(GroovyCommonClassNames.GROOVY_LANG_SCRIPT).append(' ');
    }
    else if (!isEnum && !isAnnotationType) {
      writeExtendsList(text, typeDefinition);
      writeImplementsList(text, typeDefinition, isInterface);
    }

    text.append("{\n");

    if (isEnum) {
      final GrEnumConstant[] enumConstants = ((GrEnumTypeDefinition)typeDefinition).getEnumConstants();
      for (GrEnumConstant constant : enumConstants) {
        classItemGenerator.writeEnumConstant(text, constant);
        text.append(',');
      }
      if (enumConstants.length > 0) {
        text.replace(text.length() - 1, text.length(), ";\n");
      }
    }

    writeAllMethods(text, classItemGenerator.collectMethods(typeDefinition, isClassDef), typeDefinition);

    if (typeDefinition instanceof GrTypeDefinition) {
      for (GrMembersDeclaration declaration : ((GrTypeDefinition)typeDefinition).getMemberDeclarations()) {
        if (declaration instanceof GrVariableDeclaration) {
          classItemGenerator.writeVariableDeclarations(text, (GrVariableDeclaration)declaration);
        }
      }
      for (PsiClass inner : typeDefinition.getInnerClasses()) {
        writeTypeDefinition(text, inner, false);
        text.append("\n");
      }
    }
    text.append("}");
  }

  private void writeImplementsList(StringBuilder text, PsiClass typeDefinition, boolean isInterface) {
    final Collection<PsiClassType> implementsTypes = new LinkedHashSet<PsiClassType>();
    Collections.addAll(implementsTypes, typeDefinition.getImplementsListTypes());
  /*for (PsiClass aClass : collectDelegateTypes(typeDefinition)) {
      if (aClass.isInterface()) {
        implementsTypes.add(JavaPsiFacade.getElementFactory(myProject).createType(aClass));
      } else {
        Collections.addAll(implementsTypes, aClass.getImplementsListTypes());
      }
    }*/

    if (implementsTypes.isEmpty()) return;

    text.append(isInterface ? "extends " : "implements ");
    for (PsiClassType implementsType : implementsTypes) {
      writeType(text, implementsType, typeDefinition, classNameProvider);
      text.append(", ");
    }
    if (implementsTypes.size() > 0) text.delete(text.length() - 2, text.length());
    text.append(" ");
  }

  private void writeExtendsList(StringBuilder text, PsiClass typeDefinition) {
    final PsiClassType[] extendsClassesTypes = typeDefinition.getExtendsListTypes();

    if (extendsClassesTypes.length > 0) {

      text.append("extends ");
      writeType(text, extendsClassesTypes[0], typeDefinition, classNameProvider);
      text.append(" ");
    }
  }


  private void writeAllMethods(StringBuilder text, Collection<PsiMethod> methods, PsiClass aClass) {
    Set<MethodSignature> methodSignatures = new HashSet<MethodSignature>();
    for (PsiMethod method : methods) {
      if (!shouldBeGenerated(method)) {
        continue;
      }

      if (method instanceof GrConstructor) {
        classItemGenerator.writeConstructor(text, (GrConstructor)method, aClass.isEnum());
        continue;
      }

      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0) {
        PsiParameter[] parametersCopy = new PsiParameter[parameters.length];
        PsiType[] parameterTypes = new PsiType[parameters.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          parametersCopy[i] = parameters[i];
          parameterTypes[i] = GenerationUtil.findOutParameterType(parameters[i]);
        }

        for (int i = parameters.length - 1; i >= 0; i--) {
          MethodSignature signature =
            MethodSignatureUtil.createMethodSignature(method.getName(), parameterTypes, method.getTypeParameters(), PsiSubstitutor.EMPTY);
          if (methodSignatures.add(signature)) {
            classItemGenerator.writeMethod(text, method, parametersCopy);
            text.append('\n');

          }

          PsiParameter parameter = parameters[i];
          if (!(parameter instanceof GrParameter) || !((GrParameter)parameter).isOptional()) break;
          parameterTypes = ArrayUtil.remove(parameterTypes, parameterTypes.length - 1);
          parametersCopy = ArrayUtil.remove(parametersCopy, parametersCopy.length - 1);
        }
      }
      else {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        if (methodSignatures.add(signature)) {
          classItemGenerator.writeMethod(text, method, parameters);
          text.append('\n');
        }
      }
    }
  }


  private static boolean shouldBeGenerated(PsiMethod method) {
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
