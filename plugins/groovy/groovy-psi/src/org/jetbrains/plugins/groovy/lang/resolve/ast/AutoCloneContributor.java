/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class AutoCloneContributor extends AstTransformContributor {

  @Override
  public void collectMethods(@NotNull GrTypeDefinition clazz, @NotNull Collection<PsiMethod> collector) {
    if (PsiImplUtil.getAnnotation(clazz, GroovyCommonClassNames.GROOVY_TRANSFORM_AUTO_CLONE) == null) return;

    final LightMethodBuilder clone = new LightMethodBuilder(clazz.getManager(), "clone");
    clone.addModifier(PsiModifier.PUBLIC);
    clone.setContainingClass(clazz);
    clone.addException(CloneNotSupportedException.class.getName());
    clone.setOriginInfo("created by @AutoClone");
    collector.add(clone);
  }
}
