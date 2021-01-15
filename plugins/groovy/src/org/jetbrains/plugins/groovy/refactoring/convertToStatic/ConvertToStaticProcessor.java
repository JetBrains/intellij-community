// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.VisitorCallback;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_DYNAMIC;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC;
import static org.jetbrains.plugins.groovy.refactoring.convertToStatic.ConvertToStatic.applyDeclarationFixes;
import static org.jetbrains.plugins.groovy.refactoring.convertToStatic.ConvertToStatic.applyErrorFixes;

public class ConvertToStaticProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ConvertToStaticProcessor.class);

  private final GroovyFile[] myFiles;

  public ConvertToStaticProcessor(Project project, GroovyFile... files) {
    super(project);
    myFiles = files;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new UsageViewDescriptorAdapter() {
      @Override
      public PsiElement @NotNull [] getElements() {
        return myFiles;
      }

      @Override
      public String getProcessedElementsHeader() {
        return GroovyRefactoringBundle.message("files.to.be.converted");
      }
    };
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return UsageInfo.EMPTY_ARRAY;
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    int counter = 0;
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    LOG.assertTrue(progressIndicator != null);
    progressIndicator.setIndeterminate(false);
    for (GroovyFile file : myFiles) {
      counter++;
      commitFile(file);
      progressIndicator.setText2(file.getName());
      progressIndicator.setFraction(counter / (double)myFiles.length);
      try {
        applyDeclarationFixes(file);
        putCompileAnnotations(file);
        applyErrorFixes(file);
        commitFile(file);
      }
      catch (Exception e) {
        LOG.error("Error in converting file: " + file.getName(), e);
      }
    }
  }

  private void commitFile(@NotNull GroovyFile file) {
    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
    LOG.assertTrue(document != null);
    psiDocumentManager.commitDocument(document);
    LOG.assertTrue(file.isValid());
  }

  private void putCompileAnnotations(@NotNull GroovyFile file) {
    Set<GrTypeDefinition> classesWithUnresolvedRef = new HashSet<>();
    Set<GrMethod> methodsWithUnresolvedRef = new HashSet<>();

    VisitorCallback callback = (element, info) -> {
      GrMethod containingMethod = PsiTreeUtil.getParentOfType(element, GrMethod.class);
      if (containingMethod != null) {
        methodsWithUnresolvedRef.add(containingMethod);
        return;
      }
      GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(element, GrTypeDefinition.class);
      if (containingClass != null) classesWithUnresolvedRef.add(containingClass);
    };

    file.accept(new DynamicFeaturesVisitor(file, myProject, callback));


    file.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitTypeDefinition(@NotNull GrTypeDefinition typeDef) {
        processDefinitions(typeDef, classesWithUnresolvedRef);
        super.visitTypeDefinition(typeDef);
      }

      @Override
      public void visitMethod(@NotNull GrMethod method) {
        processMethods(method, methodsWithUnresolvedRef);
        super.visitMethod(method);
      }
    });
  }

  private void processMethods(@NotNull GrMethod method, Set<GrMethod> dynamicMethods) {
    PsiElement containingClass = method.getContainingClass();
    boolean isOuterStatic = containingClass != null && CompileStaticUtil.isCompileStatic(containingClass);
    boolean isStatic = dynamicMethods.stream().noneMatch(method::isEquivalentTo);

    if (isOuterStatic != isStatic) {
      addAnnotation(method, isStatic);
    }
  }

  private void processDefinitions(GrTypeDefinition typeDef, Set<GrTypeDefinition> dynamicClasses) {
    PsiElement containingClass = typeDef.getContainingClass();
    boolean isOuterStatic = containingClass != null && CompileStaticUtil.isCompileStatic(containingClass);

    boolean isStatic = !dynamicClasses.contains(typeDef);
    if (isOuterStatic && !isStatic) {
      addAnnotation(typeDef, false);
    }
    if (!isOuterStatic && isStatic) {
      addAnnotation(typeDef, true);
    }
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return GroovyRefactoringBundle.message("converting.files.to.static");
  }

  void addAnnotation(@NotNull PsiMember owner, boolean isStatic) {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null && CompileStaticUtil.getCompileStaticAnnotation(owner) == null) {
      String annotation = isStatic ? GROOVY_TRANSFORM_COMPILE_STATIC : GROOVY_TRANSFORM_COMPILE_DYNAMIC;
      modifierList.addAnnotation(annotation);
    }
  }
}
