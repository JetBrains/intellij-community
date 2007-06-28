/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ilyas
 */
public interface GrArgumentLabel extends GroovyPsiElement {
  public PsiElement getNameElement();
}
