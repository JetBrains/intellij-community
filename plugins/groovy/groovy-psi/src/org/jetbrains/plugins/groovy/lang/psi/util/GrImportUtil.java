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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.psi.PsiFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class GrImportUtil {
  private static final LightCacheKey<MultiMap<String, String>> KEY = LightCacheKey.createByFileModificationCount();

  public static boolean acceptName(GrReferenceElement ref, String expected) {
    final String actual = ref.getReferenceName();
    if (expected.equals(actual)) return true;

    if (ref.getQualifier() != null) return false;

    final PsiFile file = ref.getContainingFile();
    if (file instanceof GroovyFile) {
      MultiMap<String, String> data = KEY.getCachedValue(file);
      if (data == null) {
        data = collectAliases((GroovyFile)file);
        KEY.putCachedValue(file, data);
      }

      final Collection<String> aliases = data.get(expected);
      return aliases.contains(actual);
    }


    return false;
  }

  @NotNull
  private static MultiMap<String, String> collectAliases(@NotNull GroovyFile file) {
    MultiMap<String, String> aliases = MultiMap.createSet();

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
}
