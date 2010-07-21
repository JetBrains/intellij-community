/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.quickedit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Quick Edit Language" intention action that provides a popup which shows an injected language
 * fragment's complete prefix and suffix in non-editable areas and allows to edit the fragment
 * without having to consider any additional escaping rules (e.g. when editing regexes in String
 * literals).
 * <p/>
 * This is a bit experimental because it doesn't play very well with some quickfixes, such as the
 * JavaScript's "Create Method/Function" one which opens another editor window. Though harmless,
 * this is quite confusing.
 * <p/>
 * I wonder if such QuickFixes should try to get an Editor from the DataContext
 * (see {@link QuickEditEditor.MyPanel#getData(java.lang.String)}) instead of using the "tactical nuke"
 * com.intellij.openapi.fileEditor.FileEditorManager#openTextEditor(com.intellij.openapi.fileEditor.OpenFileDescriptor, boolean).
 */
public class QuickEditAction implements IntentionAction {

  private String myLastLanguageName;

  @NotNull
  public String getText() {
    return "Edit "+ StringUtil.notNullize(myLastLanguageName, "Injected")+" Fragment";
  }

  @NotNull
  public String getFamilyName() {
    return "Edit Injected Fragment";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return getRangePair(file, editor.getCaretModel().getOffset()) != null;
  }

  @Nullable
  private Pair<PsiElement, TextRange> getRangePair(final PsiFile file, final int offset) {
    final PsiLanguageInjectionHost host =
      PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false);
    if (host == null) return null;
    final List<Pair<PsiElement, TextRange>> injections = InjectedLanguageUtil.getInjectedPsiFiles(host);
    if (injections == null || injections.isEmpty()) return null;
    final int offsetInElement = offset - host.getTextRange().getStartOffset();
    final Pair<PsiElement, TextRange> rangePair = ContainerUtil.find(injections, new Condition<Pair<PsiElement, TextRange>>() {
      public boolean value(final Pair<PsiElement, TextRange> pair) {
        return pair.second.containsRange(offsetInElement, offsetInElement);
      }
    });
    if (rangePair != null) {
      myLastLanguageName = rangePair.first.getContainingFile().getLanguage().getDisplayName();
    }
    return rangePair;
  }

  public void invoke(@NotNull Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    final Pair<PsiElement, TextRange> pair = getRangePair(file, offset);
    assert pair != null;
    final PsiFile injectedFile = (PsiFile)pair.first;
    final Place shreds = InjectedLanguageUtil.getShreds(injectedFile);

    final FileType fileType = injectedFile.getFileType();

    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    final String text = InjectedLanguageManager.getInstance(project).getUnescapedText(injectedFile);
    final PsiFile file2 =
        factory.createFileFromText("dummy." + fileType.getDefaultExtension(), fileType, text, LocalTimeCounter.currentTime(), true);
    file2.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, SmartPointerManager.getInstance(project).createSmartPsiElementPointer(file));
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file2);
    assert document != null;
    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(document, new ReadonlyFragmentModificationHandler() {
      public void handle(final ReadOnlyFragmentModificationException e) {
        //nothing
      }
    });

    final Map<PsiLanguageInjectionHost.Shred, RangeMarker> markers = ContainerUtil.assignValues(shreds.iterator(), new Convertor<PsiLanguageInjectionHost.Shred, RangeMarker>() {
      public RangeMarker convert(final PsiLanguageInjectionHost.Shred shred) {
        return document.createRangeMarker(shred.range.getStartOffset() + shred.prefix.length(), shred.range.getEndOffset() - shred.suffix.length());
      }
    });
    boolean first = true;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      final RangeMarker marker = markers.get(shred);
      if (first) marker.setGreedyToLeft(true);
      marker.setGreedyToRight(true); 
      first = false;
    }
    int curOffset = 0;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      final RangeMarker marker = markers.get(shred);
      final int start = marker.getStartOffset();
      final int end = marker.getEndOffset();
      if (curOffset < start) {
        final RangeMarker rangeMarker = document.createGuardedBlock(curOffset, start);
        if (curOffset == 0) rangeMarker.setGreedyToLeft(true);
      }
      curOffset = end;
    }
    if (curOffset < text.length()) {
      document.createGuardedBlock(curOffset, text.length()).setGreedyToRight(true);
    }

    final QuickEditEditor e = new QuickEditEditor(document, project, fileType, new QuickEditEditor.QuickEditSaver() {
      public void save(final String text) {
        final Map<PsiLanguageInjectionHost, Set<PsiLanguageInjectionHost.Shred>> map = ContainerUtil.classify(shreds.iterator(), new Convertor<PsiLanguageInjectionHost.Shred, PsiLanguageInjectionHost>() {
          public PsiLanguageInjectionHost convert(final PsiLanguageInjectionHost.Shred o) {
            return o.host;
          }
        });
        for (PsiLanguageInjectionHost host : map.keySet()) {
          final String hostText = host.getText();
          TextRange insideHost = null;
          final StringBuilder sb = new StringBuilder();
          for (PsiLanguageInjectionHost.Shred shred : map.get(host)) {
            final TextRange localInsideHost = shred.getRangeInsideHost();
            final TextRange localInsideFile = new TextRange(markers.get(shred).getStartOffset(), markers.get(shred).getEndOffset());
            if (insideHost != null) {
              sb.append(hostText.substring(insideHost.getEndOffset(), localInsideHost.getStartOffset()));
            }
            sb.append(localInsideFile.substring(text));

            insideHost = insideHost == null? localInsideHost : insideHost.union(localInsideHost);
          }
          assert insideHost != null;
          ElementManipulators.getManipulator(host).handleContentChange(host, insideHost, sb.toString());
        }
      }
    });
    if (!shreds.isEmpty()) {
      final int start = markers.get(shreds.get(0)).getStartOffset();
      e.getEditor().getCaretModel().moveToOffset(start);
      e.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }

    // Using the popup doesn't seem to be a good idea because there's no completion possible inside it: When the
    // completion popup closes, the quickedit popup is gone as well - but I like the movable and resizable popup :(
    final ComponentPopupBuilder builder =
        JBPopupFactory.getInstance().createComponentPopupBuilder(e.getComponent(), e.getPreferredFocusedComponent());
    builder.setMovable(true);
    builder.setResizable(true);
    builder.setRequestFocus(true);
    builder.setTitle("<html>Edit <b>" + fileType.getName() + "</b> Fragment</html>");
    builder.setAdText("Press Ctrl+Enter to save, Escape to cancel.");
    builder.setCancelCallback(new Computable<Boolean>() {
      public Boolean compute() {
        e.setCancel(true);
        try {
          e.uninstall();
        }
        catch (Exception e1) {
        }
        return Boolean.TRUE;
      }
    });
    builder.setModalContext(true);
    builder.setDimensionServiceKey(project, getClass().getSimpleName()+"DimensionKey", false);

    final JBPopup popup = builder.createPopup();
    e.install(popup);

    popup.showCenteredInCurrentWindow(project);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
