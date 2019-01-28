// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.configSlurper;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public abstract class ConfigSlurperSupport {

  public static final ExtensionPointName<ConfigSlurperSupport> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.configSlurperSupport");

  @Nullable
  public abstract PropertiesProvider getProvider(@NotNull GroovyFile file);

  @Nullable
  public PropertiesProvider getConfigSlurperInfo(@NotNull PsiElement qualifierResolve) {
    return null;
  }

  public interface PropertiesProvider {
    void collectVariants(@NotNull List<String> prefix, @NotNull PairConsumer<String, Boolean> consumer);
  }
}
