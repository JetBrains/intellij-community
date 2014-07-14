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
package org.jetbrains.plugins.groovy.configSlurper;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

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
  public PropertiesProvider getConfigSlurperInfo(@NotNull GrExpression qualifier, @NotNull PsiElement qualifierResolve) {
    return null;
  }

  public interface PropertiesProvider {
    void collectVariants(@NotNull List<String> prefix, @NotNull PairConsumer<String, Boolean> consumer);
  }

}
