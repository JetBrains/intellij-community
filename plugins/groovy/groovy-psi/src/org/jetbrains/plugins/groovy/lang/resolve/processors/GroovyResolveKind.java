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
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.scope.ElementClassHint.DeclarationKind;

import java.util.EnumSet;

enum GroovyResolveKind {

  VARIABLE(EnumSet.of(DeclarationKind.VARIABLE)),
  BINDING(EnumSet.of(DeclarationKind.VARIABLE)),
  ENUM_CONST(EnumSet.of(DeclarationKind.ENUM_CONST)),
  FIELD(EnumSet.of(DeclarationKind.FIELD)),
  PROPERTY(EnumSet.of(DeclarationKind.METHOD, DeclarationKind.FIELD)),
  METHOD(EnumSet.of(DeclarationKind.METHOD)),
  CLASS(EnumSet.of(DeclarationKind.CLASS)),
  PACKAGE(EnumSet.of(DeclarationKind.PACKAGE));

  final EnumSet<DeclarationKind> declarationKinds;

  GroovyResolveKind(EnumSet<DeclarationKind> declarationKinds) {
    this.declarationKinds = declarationKinds;
  }
}
