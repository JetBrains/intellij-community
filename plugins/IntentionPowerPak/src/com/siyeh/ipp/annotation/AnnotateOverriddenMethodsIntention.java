/*
 * Copyright 2011-2016 Bas Leijdekkers
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
package com.siyeh.ipp.annotation;

import com.intellij.codeInsight.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AnnotateOverriddenMethodsIntention extends MutablyNamedIntention {
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new AnnotateOverriddenMethodsPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAnnotation annotation = (PsiAnnotation)element;
    final String annotationText = annotation.getText();
    final PsiElement grandParent = element.getParent().getParent();
    if (grandParent instanceof PsiMethod) {
      return IntentionPowerPackBundle.message("annotate.overridden.methods.intention.method.name", annotationText);
    }
    else {
      return IntentionPowerPackBundle.message("annotate.overridden.methods.intention.parameters.name", annotationText);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiAnnotation annotation = (PsiAnnotation)element;
    final String annotationName = annotation.getQualifiedName();
    if (annotationName == null) {
      return;
    }
    final Project project = element.getProject();
    final NullableNotNullManager notNullManager = NullableNotNullManager.getInstance(project);
    final List<String> notNulls = notNullManager.getNotNulls();
    final List<String> nullables = notNullManager.getNullables();
    final List<String> annotationsToRemove;
    if (notNulls.contains(annotationName)) {
      annotationsToRemove = nullables;
    }
    else if (nullables.contains(annotationName)) {
      annotationsToRemove = notNulls;
    }
    else {
      annotationsToRemove = Collections.emptyList();
    }
    final PsiElement parent = annotation.getParent();
    final PsiElement grandParent = parent.getParent();
    final PsiMethod method;
    final int parameterIndex;
    if (!(grandParent instanceof PsiMethod)) {
      if (!(grandParent instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)grandParent;
      final PsiElement greatGrandParent = grandParent.getParent();
      if (!(greatGrandParent instanceof PsiParameterList)) {
        return;
      }
      final PsiParameterList parameterList = (PsiParameterList)greatGrandParent;
      parameterIndex = parameterList.getParameterIndex(parameter);
      final PsiElement greatGreatGrandParent = greatGrandParent.getParent();
      if (!(greatGreatGrandParent instanceof PsiMethod)) {
        return;
      }
      method = (PsiMethod)greatGreatGrandParent;
    }
    else {
      parameterIndex = -1;
      method = (PsiMethod)grandParent;
    }
    final Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(method).findAll();
    final List<PsiMethod> prepare = new ArrayList<>();
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    final Map<PsiMethod, ExternalAnnotationsManager.AnnotationPlace> annotationPlaces = new LinkedHashMap<>();
    for (PsiMethod overridingMethod : overridingMethods) {
      annotationPlaces.put(overridingMethod, annotationsManager.chooseAnnotationsPlaceNoUi(overridingMethod));
    }

    askAboutSourceRootsWithoutExternalAnnotations(project, overridingMethods, annotationsManager, annotationPlaces);

    for (PsiMethod overridingMethod : overridingMethods) {
      if (annotationPlaces.get(overridingMethod) == ExternalAnnotationsManager.AnnotationPlace.IN_CODE) {
        prepare.add(overridingMethod);
      }
    }
    
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(prepare)) {
      return;
    }
    final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    try {
      for (PsiMethod overridingMethod : overridingMethods) {
        if (parameterIndex == -1) {
          annotate(overridingMethod, annotationName, attributes, annotationsToRemove, annotationPlaces.get(overridingMethod), annotationsManager);
        }
        else {
          final PsiParameterList parameterList = overridingMethod.getParameterList();
          final PsiParameter[] parameters = parameterList.getParameters();
          final PsiParameter parameter = parameters[parameterIndex];
          annotate(parameter, annotationName, attributes, annotationsToRemove, annotationPlaces.get(overridingMethod), annotationsManager);
        }
      }
    }
    catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {
      //escape on configuring root cancel further annotations
    }
    if (!prepare.isEmpty()) {
      UndoUtil.markPsiFileForUndo(annotation.getContainingFile());
    }
  }

  private static void askAboutSourceRootsWithoutExternalAnnotations(Project project,
                                                                    Collection<PsiMethod> overridingMethods,
                                                                    ExternalAnnotationsManager annotationsManager,
                                                                    Map<PsiMethod, ExternalAnnotationsManager.AnnotationPlace> annotationPlaces) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Map<VirtualFile, ExternalAnnotationsManager.AnnotationPlace> sourceRoots = new HashMap<>();
    for (PsiMethod overridingMethod : overridingMethods) {
      ExternalAnnotationsManager.AnnotationPlace annotationPlace = annotationPlaces.get(overridingMethod);
      if (annotationPlace == ExternalAnnotationsManager.AnnotationPlace.NEED_ASK_USER) {
        //ask once per source root
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(overridingMethod);
        VirtualFile sourceRoot = virtualFile != null ? fileIndex.getSourceRootForFile(virtualFile) : null;
        if (sourceRoot != null) {
          annotationPlaces
            .put(overridingMethod,
                 sourceRoots.computeIfAbsent(sourceRoot, __ -> annotationsManager.chooseAnnotationsPlace(overridingMethod)));
        }
      }
    }
  }

  private static void annotate(PsiModifierListOwner modifierListOwner,
                               String annotationName,
                               PsiNameValuePair[] attributes,
                               List<String> annotationsToRemove,
                               ExternalAnnotationsManager.AnnotationPlace annotationAnnotationPlace,
                               ExternalAnnotationsManager annotationsManager) throws ProcessCanceledException {
    PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(modifierListOwner, annotationName);
    if (target == null || target.hasAnnotation(annotationName)) return;
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.NOWHERE) {
      return;
    }
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.EXTERNAL) {
      for (String annotationToRemove : annotationsToRemove) {
        annotationsManager.deannotate(modifierListOwner, annotationToRemove);
      }
      try {
        annotationsManager.annotateExternally(modifierListOwner, annotationName, modifierListOwner.getContainingFile(), attributes);
      }
      catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {}
    }
    else {
      WriteAction.run(() -> {
        for (String annotationToRemove : annotationsToRemove) {
          final PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierListOwner, annotationToRemove);
          if (annotation != null) {
            annotation.delete();
          }
        }
        final PsiAnnotation inserted = target.addAnnotation(annotationName);
        for (PsiNameValuePair pair : attributes) {
          inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
        }
        JavaCodeStyleManager.getInstance(modifierListOwner.getProject()).shortenClassReferences(inserted);
      });
    }
  }
}
