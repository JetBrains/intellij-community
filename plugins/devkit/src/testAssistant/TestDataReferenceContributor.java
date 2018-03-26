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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.patterns.uast.UastPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileInfoManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

import java.util.Collection;

import static org.jetbrains.idea.devkit.testAssistant.TestDataLineMarkerProvider.*;

/**
 * @author zolotov
 * @since 9/20/13
 */
public class TestDataReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar.registerUastReferenceProvider(registrar,
                                                         UastPatterns.stringLiteralExpression()
                                                                     .annotationParam(TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME, "value"),
                                                         new TestDataReferenceProvider(), PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  private static class TestDataReferenceProvider extends UastLiteralReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByULiteral(@NotNull final ULiteralExpression literalExpression,
                                                  @NotNull PsiLanguageInjectionHost host,
                                                  @NotNull final ProcessingContext context) {

      TextRange range = ElementManipulators.getValueTextRange(host);

      //ideally `literalExpression.getValue()` should be the right value for `stringValue`, but something is wrong with Kotlin
      String stringValue;
      if (literalExpression.getSourcePsi() == host) {
        stringValue = UastLiteralUtils.getValueIfStringLiteral(literalExpression);
        if (stringValue == null) return PsiReference.EMPTY_ARRAY;
      }
      else {
        StringBuilder chars = new StringBuilder();
        host.createLiteralTextEscaper().decode(TextRange.from(0, host.getTextLength()), chars);
        stringValue = chars.toString();
      }

      final TestDataReferenceSet referenceSet = new TestDataReferenceSet(stringValue,
                                                                         host, range.getStartOffset(),
                                                                         null, false);
      referenceSet.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, FileReferenceSet.ABSOLUTE_TOP_LEVEL);
      return referenceSet.getAllReferences();
    }
  }

  private static class TestDataReferenceSet extends FileReferenceSet {
    public TestDataReferenceSet(@NotNull String str,
                                @NotNull PsiElement element,
                                int startInElement,
                                @Nullable PsiReferenceProvider provider,
                                final boolean isCaseSensitive) {
      super(str, element, startInElement, provider, isCaseSensitive, true);
    }

    @Override
    public boolean isEmptyPathAllowed() {
      return false;
    }

    @Override
    public boolean isAbsolutePathReference() {
      return super.isAbsolutePathReference() || StringUtil.startsWithChar(getPathString(), '$');
    }

    @Override
    public FileReference createFileReference(TextRange range, int index, String text) {
      return new TestDataReference(this, range, index, text);
    }

    @NotNull
    @Override
    public Collection<PsiFileSystemItem> computeDefaultContexts() {
      return toFileSystemItems(ManagingFS.getInstance().getLocalRoots());
    }

    @Override
    protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
      return DIRECTORY_FILTER;
    }
  }

  public static class TestDataReference extends FileReference {
    public TestDataReference(@NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
      super(fileReferenceSet, range, index, text);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      if (getIndex() == 0 && !StringUtil.startsWithChar(getFileReferenceSet().getPathString(), '/')) {
        Collection<Object> variants = ContainerUtil.newHashSet(super.getVariants());
        final PsiDirectory projectPsiRoot = getProjectPsiRoot();
        if (projectPsiRoot != null) {
          variants.add(FileInfoManager.getFileLookupItem(projectPsiRoot, PROJECT_ROOT_VARIABLE, projectPsiRoot.getIcon(0))
                         .withTypeText(projectPsiRoot.getVirtualFile().getPath(), true));
        }

        final PsiDirectory contentPsiRoot = getContentPsiRoot();
        if (contentPsiRoot != null) {
          variants.add(FileInfoManager.getFileLookupItem(contentPsiRoot, CONTENT_ROOT_VARIABLE, contentPsiRoot.getIcon(0))
                         .withTypeText(contentPsiRoot.getVirtualFile().getPath(), true));
        }
        return ArrayUtil.toObjectArray(variants);
      }

      return super.getVariants();
    }

    @NotNull
    @Override
    protected ResolveResult[] innerResolve(boolean caseSensitive, @NotNull PsiFile containingFile) {
      if (getIndex() == 0 && StringUtil.startsWithChar(getText(), '$')) {
        if (PROJECT_ROOT_VARIABLE.equals(getText())) {
          final PsiDirectory projectPsiRoot = getProjectPsiRoot();
          if (projectPsiRoot != null) {
            return new ResolveResult[]{new PsiElementResolveResult(projectPsiRoot)};
          }
        }
        else if (CONTENT_ROOT_VARIABLE.equals(getText())) {
          final PsiDirectory contentPsiRoot = getContentPsiRoot();
          if (contentPsiRoot != null) {
            return new ResolveResult[]{new PsiElementResolveResult(contentPsiRoot)};
          }
        }
      }
      return super.innerResolve(caseSensitive, containingFile);
    }

    @Nullable
    private PsiDirectory getProjectPsiRoot() {
      final Project project = getElement().getProject();
      final VirtualFile projectDir = project.getBaseDir();
      if (projectDir != null) {
        final PsiManager psiManager = PsiManager.getInstance(project);
        return psiManager.findDirectory(projectDir);
      }
      return null;
    }

    @Nullable
    private PsiDirectory getContentPsiRoot() {
      final Project project = getElement().getProject();
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      final VirtualFile file = getElement().getContainingFile().getOriginalFile().getVirtualFile();
      if (file != null) {
        final VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
        if (contentRoot != null) {
          final PsiManager psiManager = PsiManager.getInstance(project);
          return psiManager.findDirectory(contentRoot);
        }
      }
      return null;
    }
  }
}
