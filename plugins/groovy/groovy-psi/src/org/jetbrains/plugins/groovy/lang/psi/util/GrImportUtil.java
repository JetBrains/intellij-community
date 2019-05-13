// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
