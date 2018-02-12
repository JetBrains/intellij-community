// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaGlobalMemberLookupElement;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

class StaticImportInsertHandler implements InsertHandler<JavaGlobalMemberLookupElement> {
  public static final InsertHandler<JavaGlobalMemberLookupElement> INSTANCE = new StaticImportInsertHandler();

  private StaticImportInsertHandler() {
  }

  private static boolean importAlreadyExists(final PsiMember member, final GroovyFile file, final PsiElement place) {
    final PsiManager manager = file.getManager();
    PsiScopeProcessor processor = new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        return !manager.areElementsEquivalent(element, member);
      }
    };

    boolean skipStaticImports = member instanceof PsiClass;
    final GrImportStatement[] imports = file.getImportStatements();
    final ResolveState initial = ResolveState.initial();
    for (GrImportStatement anImport : imports) {
      if (skipStaticImports == anImport.isStatic()) continue;
      if (!anImport.processDeclarations(processor, initial, null, place)) return true;
    }
    return false;
  }

  @Override
  public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
    GroovyInsertHandler.INSTANCE.handleInsert(context, item);
    final PsiMember member = item.getObject();
    PsiDocumentManager.getInstance(member.getProject()).commitDocument(context.getDocument());
    final GrReferenceExpression ref = PsiTreeUtil.
      findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);

    if (ref != null &&
        ref.getQualifier() == null &&
        context.getFile() instanceof GroovyFile &&
        !importAlreadyExists(member, ((GroovyFile)context.getFile()), ref) &&
        !PsiManager.getInstance(context.getProject()).areElementsEquivalent(ref.resolve(), member)) {
      ref.bindToElementViaStaticImport(member);
    }

  }
}
