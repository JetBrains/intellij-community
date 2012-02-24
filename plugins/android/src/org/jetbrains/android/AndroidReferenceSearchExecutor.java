/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters params, @NotNull Processor<PsiReference> consumer) {
    final PsiElement refElement = params.getElementToSearch();

    if (!(refElement instanceof PsiFile)) {
      return;
    }
    final VirtualFile vFile = ((PsiFile)refElement).getVirtualFile();
    if (vFile == null) {
      return;
    }
    LocalResourceManager manager = LocalResourceManager.getInstance(refElement);
    if (manager == null) {
      return;
    }

    String resType = manager.getFileResourceType((PsiFile)refElement);
    if (resType != null) {
      String resName = AndroidCommonUtils.getResourceName(resType, vFile.getName());
      // unless references can be found by a simple CachedBasedRefSearcher
      if (!resName.equals(vFile.getNameWithoutExtension()) && StringUtil.isNotEmpty(resName)) {
        params.getOptimizer().searchWord(resName, params.getEffectiveSearchScope(), true, refElement);
      }
    }
  }
}
