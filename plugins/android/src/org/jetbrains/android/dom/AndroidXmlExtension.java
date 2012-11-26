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
package org.jetbrains.android.dom;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.DefaultXmlExtension;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlExtension extends DefaultXmlExtension {
  @Nullable
  @Override
  public TagNameReference createTagNameReference(ASTNode nameElement, boolean startTagFlag) {
    return AndroidXmlReferenceProvider.areReferencesProvidedByReferenceProvider(nameElement)
           ? null
           : new AndroidClassTagNameReference(nameElement, startTagFlag);
  }

  @Override
  public boolean isAvailable(final PsiFile file) {
    if (file instanceof XmlFile) {
      if (!DirectoryIndex.getInstance(file.getProject()).isInitialized()) {
        return false;
      }
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          final Module module = ModuleUtilCore.findModuleForPsiElement(file);
          if (module == null ||
              module.isDisposed() ||
              AndroidFacet.getInstance(module) == null) {
            return false;
          }
          return AndroidResourceUtil.isInResourceSubdirectory(file, null) ||
                 ManifestDomFileDescription.isManifestFile((XmlFile)file);
        }
      });
    }
    return false;
  }
}
