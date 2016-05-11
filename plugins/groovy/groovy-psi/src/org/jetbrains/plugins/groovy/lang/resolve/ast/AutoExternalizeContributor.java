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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @author Max Medvedev
 */
public class AutoExternalizeContributor implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    GrTypeDefinition clazz = context.getCodeClass();
    if (!hasGeneratedImplementations(clazz)) return;

    final LightMethodBuilder write = new LightMethodBuilder(clazz.getManager(), "writeExternal");
    write.addParameter("out", ObjectOutput.class.getName());
    write.addException(IOException.class.getName());
    write.setOriginInfo("created by @AutoExternalize");
    context.addMethod(write);

    final LightMethodBuilder read = new LightMethodBuilder(clazz.getManager(), "readExternal");
    read.addParameter("oin", ObjectInput.class.getName());
    read.setOriginInfo("created by @AutoExternalize");
    context.addMethod(read);
  }

  private static boolean hasGeneratedImplementations(GrTypeDefinition clazz) {
    return PsiImplUtil.getAnnotation(clazz, GroovyCommonClassNames.GROOVY_TRANSFORM_AUTO_EXTERNALIZE) != null;
  }
}
