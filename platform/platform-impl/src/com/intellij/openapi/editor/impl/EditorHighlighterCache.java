// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.impl.id.PlatformIdTableBuilding;
import com.intellij.psi.impl.search.LexerEditorHighlighterLexer;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * @author yole
 */
public final class EditorHighlighterCache {
  private static final Key<WeakReference<EditorHighlighter>> ourSomeEditorSyntaxHighlighter = Key.create("some editor highlighter");

  private EditorHighlighterCache() {
  }

  public static void rememberEditorHighlighterForCachesOptimization(Document document, @NotNull final EditorHighlighter highlighter) {
    document.putUserData(ourSomeEditorSyntaxHighlighter, new WeakReference<>(highlighter));
  }

  @Nullable
  public static EditorHighlighter getEditorHighlighterForCachesBuilding(Document document) {
    if (document == null) {
      return null;
    }
    final WeakReference<EditorHighlighter> editorHighlighterWeakReference = document.getUserData(ourSomeEditorSyntaxHighlighter);
    final EditorHighlighter someEditorHighlighter = SoftReference.dereference(editorHighlighterWeakReference);

    if (someEditorHighlighter instanceof LexerEditorHighlighter &&
        ((LexerEditorHighlighter)someEditorHighlighter).isValid()
      ) {
      return someEditorHighlighter;
    }
    document.putUserData(ourSomeEditorSyntaxHighlighter, null);
    return null;
  }

  @Nullable
  public static Lexer getLexerBasedOnLexerHighlighter(CharSequence text, VirtualFile virtualFile, Project project) {
    EditorHighlighter highlighter = null;

    PsiFile psiFile = virtualFile != null ? PsiManager.getInstance(project).findFile(virtualFile) : null;
    final Document document = psiFile != null ? PsiDocumentManager.getInstance(project).getDocument(psiFile) : null;
    final EditorHighlighter cachedEditorHighlighter;
    boolean alreadyInitializedHighlighter = false;

    if (document != null &&
        (cachedEditorHighlighter = getEditorHighlighterForCachesBuilding(document)) != null &&
        PlatformIdTableBuilding.checkCanUseCachedEditorHighlighter(text, cachedEditorHighlighter)) {
      highlighter = cachedEditorHighlighter;
      alreadyInitializedHighlighter = true;
    }
    else if (virtualFile != null) {
      highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, virtualFile);
    }

    if (highlighter != null) {
      return new LexerEditorHighlighterLexer(highlighter, alreadyInitializedHighlighter);
    }
    return null;
  }
}
