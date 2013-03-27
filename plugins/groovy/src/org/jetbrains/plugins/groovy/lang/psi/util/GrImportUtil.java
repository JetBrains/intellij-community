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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class GrImportUtil {
  public static boolean acceptName(GrReferenceElement ref, String expected) {
    final String actual = ref.getReferenceName();
    if (expected.equals(actual)) return true;

    if (ref.getQualifier() != null) return false;

    final PsiFile file = ref.getContainingFile();
    if (file instanceof GroovyFile) {
      CachedValue<MultiMap<String, String>> data = file.getCopyableUserData(KEY);
      if (data == null) {
        data = CachedValuesManager.getManager(ref.getProject()).createCachedValue(new CachedValueProvider<MultiMap<String, String>>() {
          @Override
          public Result<MultiMap<String, String>> compute() {
            MultiMap<String, String> aliases = collectAliases((GroovyFile)file);

            return Result.create(aliases, PsiDocumentManager.getInstance(file.getProject()).getDocument(file));
          }
        }, false);
      }

      final MultiMap<String, String> map = data.getValue();
      final Collection<String> aliases = map.get(expected);
      return aliases.contains(actual);
    }


    return false;
  }

  @NotNull
  private static MultiMap<String, String> collectAliases(@NotNull GroovyFile file) {
    MultiMap<String, String> aliases = new MultiMap<String, String>() {
      @Override
      protected Collection<String> createCollection() {
        return ContainerUtil.newHashSet();
      }
    };

    for (GrImportStatement anImport : file.getImportStatements()) {
      if (anImport.isAliasedImport()) {
        final GrCodeReferenceElement importReference = anImport.getImportReference();
        if (importReference != null) {
          final String refName = importReference.getReferenceName();
          if (refName != null) {
            aliases.putValue(refName, anImport.getImportedName());
          }
        }
      }
    }
    return aliases;
  }

  private static final Key<CachedValue<MultiMap<String, String>>> KEY = Key.create("aliases_key");
}
