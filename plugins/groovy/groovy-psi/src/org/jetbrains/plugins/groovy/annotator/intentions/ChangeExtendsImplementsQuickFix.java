// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
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

public class ChangeExtendsImplementsQuickFix extends PsiUpdateModCommandAction<GrTypeDefinition> {

  public ChangeExtendsImplementsQuickFix(@NotNull GrTypeDefinition aClass) {
    super(aClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("change.implements.and.extends.classes");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull GrTypeDefinition typeDef, @NotNull ModPsiUpdater updater) {
    Set<String> classes = new LinkedHashSet<>();
    Set<String> interfaces = new LinkedHashSet<>();
    Set<String> unknownClasses = new LinkedHashSet<>();
    Set<String> unknownInterfaces = new LinkedHashSet<>();

    GrExtendsClause extendsClause = typeDef.getExtendsClause();
    GrImplementsClause implementsClause = typeDef.getImplementsClause();
    Project project = context.project();

    if (extendsClause != null) {
      collectRefs(extendsClause.getReferenceElementsGroovy(), classes, interfaces, typeDef.isInterface() ? unknownInterfaces : unknownClasses);
      extendsClause.delete();
    }

    if (implementsClause != null) {
      collectRefs(implementsClause.getReferenceElementsGroovy(), classes, interfaces, unknownInterfaces);
      implementsClause.delete();
    }

    if (typeDef.isInterface()) {
      interfaces.addAll(classes);
      unknownInterfaces.addAll(unknownClasses);
      addNewClause(typeDef, interfaces, unknownInterfaces, project, true);
    }
    else {
      addNewClause(typeDef, classes, unknownClasses, project, true);
      addNewClause(typeDef, interfaces, unknownInterfaces, project, false);
    }
  }

  private static void collectRefs(GrCodeReferenceElement[] refs, Collection<? super String> classes, Collection<? super String> interfaces, Collection<? super String> unknown) {
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

  private static void addNewClause(@NotNull GrTypeDefinition typeDef,
                                   @NotNull Collection<String> elements,
                                   @NotNull Collection<String> additional,
                                   @NotNull Project project,
                                   boolean isExtends) throws IncorrectOperationException {
    if (elements.isEmpty() && additional.isEmpty()) return;

    @NlsSafe StringBuilder classText = new StringBuilder();
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

    PsiElement addedClause = typeDef.addBefore(clause, typeDef.getBody());
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedClause);
  }
}
