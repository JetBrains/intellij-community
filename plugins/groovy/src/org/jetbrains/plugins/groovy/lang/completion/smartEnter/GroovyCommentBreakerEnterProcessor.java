/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.smartEnter;

import com.intellij.codeInsight.editorActions.smartEnter.EnterProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * @author Dmitry.Krasilschikov
 * Date: 29.07.2008
 */
public class GroovyCommentBreakerEnterProcessor implements EnterProcessor {
    public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
        return false;
    }
}
