// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrFinalListener {
  private static final Logger LOG = Logger.getInstance(GrFinalListener.class);
  private final Editor myEditor;

  public GrFinalListener(Editor editor) {
    myEditor = editor;
  }

  public void perform(boolean generateFinal, @NotNull GrVariable variable) {
    final GrModifierList modifierList = variable.getModifierList();
    LOG.assertTrue(modifierList != null);
    if (modifierList.hasModifierProperty(PsiModifier.FINAL) == generateFinal) return;
    if (modifierList.getModifiers().length == 1) return;

    final Document document = myEditor.getDocument();
    final int textOffset = modifierList.getTextOffset();

    final Runnable runnable = () -> {
      if (generateFinal) {
        final GrTypeElement typeElement = variable.getTypeElementGroovy();
        final int typeOffset = typeElement != null ? typeElement.getTextOffset() : textOffset;
        document.insertString(typeOffset, PsiModifier.FINAL + " ");
      }
      else {
        final int idx = modifierList.getText().indexOf(PsiModifier.FINAL);
        if (idx < 0) return;
        document.deleteString(textOffset + idx, textOffset + idx + PsiModifier.FINAL.length() + 1);
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

