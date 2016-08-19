/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.09.2007
 */
public class ChangeExtendsImplementsQuickFix implements IntentionAction {
  @Nullable
  private final GrExtendsClause myExtendsClause;
  @Nullable
  private final GrImplementsClause myImplementsClause;
  @NotNull
  private final GrTypeDefinition myClass;

  public ChangeExtendsImplementsQuickFix(@NotNull GrTypeDefinition aClass) {
    myClass = aClass;
    myExtendsClause = aClass.getExtendsClause();
    myImplementsClause = aClass.getImplementsClause();
  }

  @Override
  @NotNull
  public String getText() {
    return GroovyBundle.message("change.implements.and.extends.classes");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myClass.isValid() && myClass.getManager().isInProject(file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Set<String> classes = new LinkedHashSet<>();
    Set<String> interfaces = new LinkedHashSet<>();
    Set<String> unknownClasses = new LinkedHashSet<>();
    Set<String> unknownInterfaces = new LinkedHashSet<>();

    if (myExtendsClause != null) {
      collectRefs(myExtendsClause.getReferenceElementsGroovy(), classes, interfaces, myClass.isInterface() ? unknownInterfaces : unknownClasses);
      myExtendsClause.delete();
    }

    if (myImplementsClause != null) {
      collectRefs(myImplementsClause.getReferenceElementsGroovy(), classes, interfaces, unknownInterfaces);
      myImplementsClause.delete();
    }

    if (myClass.isInterface()) {
      interfaces.addAll(classes);
      unknownInterfaces.addAll(unknownClasses);
      addNewClause(interfaces, unknownInterfaces, project, true);
    }
    else {
      addNewClause(classes, unknownClasses, project, true);
      addNewClause(interfaces, unknownInterfaces, project, false);
    }
  }

  private static void collectRefs(GrCodeReferenceElement[] refs, Collection<String> classes, Collection<String> interfaces, Collection<String> unknown) {
    for (GrCodeReferenceElement ref : refs) {
      final PsiElement extendsElement = ref.resolve();
      String canonicalText = ref.getCanonicalText();

      if (extendsElement instanceof PsiClass) {
        if (((PsiClass)extendsElement).isInterface()) {
          interfaces.add(canonicalText);
        }
        else {
          classes.add(canonicalText);
        }
      }
      else {
        unknown.add(canonicalText);
      }
    }
  }

  private void addNewClause(Collection<String> elements, Collection<String> additional, Project project, boolean isExtends) throws IncorrectOperationException {
    if (elements.isEmpty() && additional.isEmpty()) return;

    StringBuilder classText = new StringBuilder();
    classText.append("class A ");
    classText.append(isExtends ? "extends " : "implements ");

    for (String str : elements) {
      classText.append(str);
      classText.append(", ");
    }

    for (String str : additional) {
      classText.append(str);
      classText.append(", ");
    }

    classText.delete(classText.length() - 2, classText.length());

    classText.append(" {}");

    final GrTypeDefinition definition = GroovyPsiElementFactory.getInstance(project).createTypeDefinition(classText.toString());
    GroovyPsiElement clause = isExtends ? definition.getExtendsClause() : definition.getImplementsClause();
    assert clause != null;

    PsiElement addedClause = myClass.addBefore(clause, myClass.getBody());
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedClause);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
