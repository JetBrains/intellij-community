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
package org.jetbrains.plugins.gradle.service.resolve;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;

/**
 * @author Vladislav.Soroka
 * @since 8/30/13
 */
public class GradleResolverUtil {

  public static int getGrMethodArumentsCount(GrArgumentList args) {
    int argsCount = 0;
    boolean namedArgProcessed = false;
    for (GroovyPsiElement arg : args.getAllArguments()) {
      if (arg instanceof GrNamedArgument) {
        if (!namedArgProcessed) {
          namedArgProcessed = true;
          argsCount++;
        }
      }
      else {
        argsCount++;
      }
    }
    return argsCount;
  }
}
