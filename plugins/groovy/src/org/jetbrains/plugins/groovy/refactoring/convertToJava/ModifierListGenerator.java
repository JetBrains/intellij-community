/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Medvedev Max
 */
public class ModifierListGenerator {

  public static final String[] JAVA_MODIFIERS = new String[]{
    PsiModifier.PUBLIC,
    PsiModifier.PROTECTED,
    PsiModifier.PRIVATE,
    //PsiModifier.PACKAGE_LOCAL,
    PsiModifier.STATIC,
    PsiModifier.ABSTRACT,
    PsiModifier.FINAL,
    PsiModifier.NATIVE,
    PsiModifier.SYNCHRONIZED,
    PsiModifier.STRICTFP,
    PsiModifier.TRANSIENT,
    PsiModifier.VOLATILE
  };

  public static final String[] JAVA_MODIFIERS_WITHOUT_ABSTRACT = new String[]{
    PsiModifier.PUBLIC,
  PsiModifier.PROTECTED,
  PsiModifier.PRIVATE,
  //PsiModifier.PACKAGE_LOCAL,
  PsiModifier.STATIC,
  //PsiModifier.ABSTRACT,
  PsiModifier.FINAL,
  PsiModifier.NATIVE,
  PsiModifier.SYNCHRONIZED ,
  PsiModifier.STRICTFP ,
  PsiModifier.TRANSIENT ,
  PsiModifier.VOLATILE
  };

  public static final String[] ENUM_CONSTRUCTOR_MODIFIERS = new String[]{
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
  };

  private ModifierListGenerator() {
  }

  public static boolean writeModifiers(StringBuilder text, PsiModifierList modifierList) {
    return writeModifiers(text, modifierList, JAVA_MODIFIERS, true);
  }


  public static boolean writeModifiers(StringBuilder text, PsiModifierList modifierList, String[] modifiers) {
    return writeModifiers(text, modifierList, modifiers, true);
  }

  public static boolean writeModifiers(StringBuilder builder, PsiModifierList modifierList, String[] modifiers, boolean writeAnnotations) {
    boolean wasAddedModifiers = false;

    if (writeAnnotations && modifierList instanceof GrModifierList) {
      GrAnnotation[] annotations = ((GrModifierList)modifierList).getAnnotations();
      AnnotationGenerator annotationGenerator = new AnnotationGenerator(builder, new ExpressionContext(modifierList.getProject(), GroovyFile.EMPTY_ARRAY));
      wasAddedModifiers = annotations.length > 0;
      for (GrAnnotation annotation : annotations) {
        annotation.accept(annotationGenerator);
        builder.append(' ');
      }
    }

    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        builder.append(modifierType);
        builder.append(' ');
        wasAddedModifiers = true;
      }
    }



    return wasAddedModifiers;
  }

  public static void writeClassModifiers(StringBuilder text,
                                         @Nullable PsiModifierList modifierList,
                                         boolean isInterface,
                                         boolean isEnum,
                                         boolean toplevel,
                                         boolean generateAnnotations) {
    if (modifierList == null) {
      text.append("public ");
      return;
    }

    List<String> allowedModifiers = new ArrayList<>();
    allowedModifiers.add(PsiModifier.PUBLIC);
    if (!isEnum) allowedModifiers.add(PsiModifier.FINAL);
    if (!toplevel) {
      allowedModifiers.addAll(Arrays.asList(PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC));
    }
    if (!isInterface && !isEnum) {
      allowedModifiers.add(PsiModifier.ABSTRACT);
    }

    writeModifiers(text, modifierList, ArrayUtil.toStringArray(allowedModifiers), generateAnnotations);
  }
}
