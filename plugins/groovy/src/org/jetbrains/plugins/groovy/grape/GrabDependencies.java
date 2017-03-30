/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

import java.util.List;

import static org.jetbrains.plugins.groovy.grape.GrapeHelper.findGrabAnnotations;

/**
 * @author peter
 */
public class GrabDependencies implements IntentionAction {

  public static final String GRAPE_RUNNER = "org.jetbrains.plugins.groovy.grape.GrapeRunner";

  @Override
  @NotNull
  public String getText() {
    return "Grab the artifacts";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Grab";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!isCorrectModule(file)) return false;

    int offset = editor.getCaretModel().getOffset();
    final GrAnnotation anno = PsiTreeUtil.findElementOfClassAtOffset(file, offset, GrAnnotation.class, false);
    if (anno != null && isGrabAnnotation(anno)) {
      return true;
    }

    PsiElement at = file.findElementAt(offset);
    if (at != null && isUnresolvedRefName(at) && findGrab(file) != null) {
      return true;
    }

    return false;
  }

  private static PsiAnnotation findGrab(final PsiFile file) {
    if (!(file instanceof GroovyFile)) return null;

    return CachedValuesManager.getCachedValue(file, () -> {
      List<PsiAnnotation> annotations = findGrabAnnotations(file.getProject(), new LocalSearchScope(file));
      return CachedValueProvider.Result.create(annotations.size() > 0 ? annotations.get(0) : null, file);
    });
  }


  private static boolean isUnresolvedRefName(@NotNull PsiElement at) {
    PsiElement parent = at.getParent();
    return parent instanceof GrReferenceElement && ((GrReferenceElement)parent).getReferenceNameElement() == at && ((GrReferenceElement)parent).resolve() == null;
  }

  private static boolean isGrabAnnotation(@NotNull GrAnnotation anno) {
    final String qname = anno.getQualifiedName();
    return qname != null && (qname.startsWith(GrabAnnos.GRAB_ANNO) || GrabAnnos.GRAPES_ANNO.equals(qname));
  }

  private static boolean isCorrectModule(PsiFile file) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) {
      return false;
    }

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null) {
      return false;
    }

    return file.getOriginalFile().getVirtualFile() != null && sdk.getSdkType() instanceof JavaSdkType;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    new GrabStartupActivity().runActivity(project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
