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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrFinalListener {
  private final Editor myEditor;
  private static final Logger LOG = Logger.getInstance("#" + GrFinalListener.class.getName());

  public GrFinalListener(Editor editor) {
    myEditor = editor;
  }

  public void perform(final boolean generateFinal, GrVariable variable) {
    perform(generateFinal, PsiModifier.FINAL, variable);
  }

  public void perform(final boolean generateFinal, final String modifier, final GrVariable variable) {
    final Document document = myEditor.getDocument();
    LOG.assertTrue(variable != null);
    final GrModifierList modifierList = variable.getModifierList();
    LOG.assertTrue(modifierList != null);
    final int textOffset = modifierList.getTextOffset();

    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (generateFinal) {
          final GrTypeElement typeElement = variable.getTypeElementGroovy();
          final int typeOffset = typeElement != null ? typeElement.getTextOffset() : textOffset;
          document.insertString(typeOffset, modifier + " ");
        }
        else {
          final int idx = modifierList.getText().indexOf(modifier);
          document.deleteString(textOffset + idx, textOffset + idx + modifier.length() + 1);
        }
      }
    };
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    if (lookup != null) {
      lookup.performGuardedChange(runnable);
    } else {
      runnable.run();
    }
    PsiDocumentManager.getInstance(variable.getProject()).commitDocument(document);
  }
}

