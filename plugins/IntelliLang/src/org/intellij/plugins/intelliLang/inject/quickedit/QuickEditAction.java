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
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Quick Edit Language" intention action that provides an editor which shows an injected language
 * fragment's complete prefix and suffix in non-editable areas and allows to edit the fragment
 * without having to consider any additional escaping rules (e.g. when editing regexes in String
 * literals).
 * 
 * @author Gregory Shrago
 * @author Konstantin Bulenkov
 */
public class QuickEditAction implements IntentionAction, LowPriorityAction {
  private String myLastLanguageName;

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return getRangePair(file, editor) != null;
  }

  @Nullable
  protected Pair<PsiElement, TextRange> getRangePair(final PsiFile file, final Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
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

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    final Pair<PsiElement, TextRange> pair = getRangePair(file, editor);
    assert pair != null;
    final PsiFile injectedFile = (PsiFile)pair.first;
    final int injectedOffset = ((DocumentWindow)PsiDocumentManager.getInstance(project).getDocument(injectedFile)).hostToInjected(offset);
    getHandler(project, injectedFile, editor, file).navigate(injectedOffset);
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static final Key<MyHandler> QUICK_EDIT_HANDLER = Key.create("QUICK_EDIT_HANDLER");

  @NotNull
  private MyHandler getHandler(Project project, PsiFile injectedFile, Editor editor, PsiFile origFile) {
    MyHandler handler = injectedFile.getUserData(QUICK_EDIT_HANDLER);
    if (handler != null && handler.isValid()) {
      return handler;
    }
    handler = new MyHandler(project, injectedFile, origFile, editor);
    injectedFile.putUserData(QUICK_EDIT_HANDLER, handler);
    return handler;
  }
  
  protected boolean isShowInBalloon() {
    return false;
  }
  
  @Nullable
  protected JComponent createBalloonComponent(PsiFile file, Ref<Balloon> ref) {
    return null;
  }

  @NotNull
  public String getText() {
    return "Edit "+ StringUtil.notNullize(myLastLanguageName, "Injected")+" Fragment";
  }

  @NotNull
  public String getFamilyName() {
    return "Edit Injected Fragment";
  }    

  private class MyHandler extends DocumentAdapter implements Disposable {
    private final Project myProject;
    private final PsiFile myInjectedFile;
    private final Editor myEditor;
    private final Document myOrigDocument;
    private final PsiFile myNewFile;
    private final LightVirtualFile myNewVirtualFile;
    private final Document myNewDocument;
    private final Map<SmartPsiElementPointer, Pair<RangeMarker, RangeMarker>> myMarkers =
      new LinkedHashMap<SmartPsiElementPointer, Pair<RangeMarker, RangeMarker>>();
    private EditorWindow mySplittedWindow;
    private boolean myReleased;

    private MyHandler(Project project, PsiFile injectedFile, final PsiFile origFile, Editor editor) {
      myProject = project;
      myInjectedFile = injectedFile;
      myEditor = editor;
      myOrigDocument = editor.getDocument();
      final Place shreds = InjectedLanguageUtil.getShreds(myInjectedFile);
      final FileType fileType = injectedFile.getFileType();
      final Language language = injectedFile.getLanguage();

      final PsiFileFactory factory = PsiFileFactory.getInstance(project);
      final String text = InjectedLanguageManager.getInstance(project).getUnescapedText(injectedFile);
      final String newFileName =
        StringUtil.notNullize(language.getDisplayName(), "Injected") + " Fragment " + "(" +
        origFile.getName() + ":" + shreds.get(0).host.getTextRange().getStartOffset() + ")" + "." + fileType.getDefaultExtension();
      myNewFile = factory.createFileFromText(newFileName, language, text, true, true);
      myNewVirtualFile = (LightVirtualFile)myNewFile.getVirtualFile();
      assert myNewVirtualFile != null;
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
      myNewFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, smartPointerManager.createSmartPsiElementPointer(origFile));
      myNewDocument = PsiDocumentManager.getInstance(project).getDocument(myNewFile);
      assert myNewDocument != null;
      EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(myNewDocument, new ReadonlyFragmentModificationHandler() {
        public void handle(final ReadOnlyFragmentModificationException e) {
          //nothing
        }
      });
      myOrigDocument.addDocumentListener(this);      
      myNewDocument.addDocumentListener(new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              commitToOriginal();
            }
          });
        }
      });
      EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryAdapter() {
        @Override
        public void editorCreated(EditorFactoryEvent event) {
          if (event.getEditor().getDocument() == myNewDocument) {
            new AnAction() {
              @Override
              public void actionPerformed(AnActionEvent e) {
                closeEditor();
              }
            }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, event.getEditor().getContentComponent());
          }
        }

        @Override
        public void editorReleased(EditorFactoryEvent event) {
          if (event.getEditor().getDocument() == myNewDocument) {
            Disposer.dispose(MyHandler.this);
            myReleased = true;
            myOrigDocument.removeDocumentListener(MyHandler.this);
            myInjectedFile.putUserData(QUICK_EDIT_HANDLER, null);
          }
        }
      }, this);
      initMarkers(shreds);
    }

    public boolean isValid() {
      return myNewVirtualFile.isValid() && myInjectedFile.isValid();
    }

    public void navigate(int injectedOffset) {
      if (isShowInBalloon()) {
        Ref<Balloon> ref = Ref.create(null);
        final JComponent component = createBalloonComponent(myNewFile, ref);
        if (component != null) {
          final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(component)
            .setShadow(true)
            .setAnimationCycle(0)
            .setHideOnClickOutside(true)
            .setHideOnKeyOutside(true)
            .setHideOnAction(false)
            .setFillColor(UIUtil.getControlColor())
            .createBalloon();
          ref.set(balloon);
          Disposer.register(myNewFile.getProject(), balloon);
          final Balloon.Position position = getBalloonPosition(myEditor);
          RelativePoint point = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
          if (position == Balloon.Position.above) {
            final Point p = point.getPoint();
            point = new RelativePoint(point.getComponent(), new Point(p.x, p.y - myEditor.getLineHeight()));
          }
          balloon.show(point, position);
        }
      } else {
        final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(myProject);
        final FileEditor[] editors = fileEditorManager.getEditors(myNewVirtualFile);
        if (editors.length == 0) {
          final EditorWindow curWindow = fileEditorManager.getCurrentWindow();
          mySplittedWindow = curWindow.split(SwingConstants.HORIZONTAL, false, myNewVirtualFile, true);
        }
        fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myNewVirtualFile, injectedOffset), true);
      }
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      closeEditor();
    }

    private void closeEditor() {
      boolean unsplit = false;
      if (mySplittedWindow != null && !mySplittedWindow.isDisposed()) {
        final EditorWithProviderComposite[] editors = mySplittedWindow.getEditors();
        if (editors.length == 1 && editors[0].getFile() == myNewVirtualFile) {
          unsplit = true;
        }
      }
      FileEditorManager.getInstance(myProject).closeFile(myNewVirtualFile);
      if (unsplit) {
        for (EditorWindow editorWindow : mySplittedWindow.findSiblings()) {
          editorWindow.unsplit(true);
        }
      }
    }

    public void initMarkers(final Place shreds) {
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
      for (PsiLanguageInjectionHost.Shred shred : shreds) {
        final RangeMarker rangeMarker = myNewDocument
          .createRangeMarker(shred.range.getStartOffset() + shred.prefix.length(), shred.range.getEndOffset() - shred.suffix.length());
        final TextRange rangeInsideHost = shred.getRangeInsideHost();
        final RangeMarker origMarker =
          myOrigDocument.createRangeMarker(rangeInsideHost.shiftRight(shred.host.getTextRange().getStartOffset()));
        myMarkers.put(smartPointerManager.createSmartPsiElementPointer(shred.host), Pair.create(origMarker, rangeMarker));
      }
      for (Pair<RangeMarker, RangeMarker> markers : myMarkers.values()) {
        markers.first.setGreedyToLeft(true);
        markers.second.setGreedyToLeft(true);
        markers.first.setGreedyToRight(true);
        markers.second.setGreedyToRight(true);
      }
      int curOffset = 0;
      for (Pair<RangeMarker, RangeMarker> markerPair : myMarkers.values()) {
        final RangeMarker marker = markerPair.second;
        final int start = marker.getStartOffset();
        final int end = marker.getEndOffset();
        if (curOffset < start) {
          final RangeMarker rangeMarker = myNewDocument.createGuardedBlock(curOffset, start);
          if (curOffset == 0) rangeMarker.setGreedyToLeft(true);
        }
        curOffset = end;
      }
      if (curOffset < myNewDocument.getTextLength()) {
        final RangeMarker rangeMarker = myNewDocument.createGuardedBlock(curOffset, myNewDocument.getTextLength());
        rangeMarker.setGreedyToRight(true);
      }
    }

    private void commitToOriginal() {
      if (!isValid()) return;
      final PsiFile origFile = (PsiFile)myNewFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT).getElement();
      if (!myReleased) myOrigDocument.removeDocumentListener(this);
      try {
        new WriteCommandAction.Simple(myProject, origFile) {
          @Override
          protected void run() throws Throwable {
            PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(new Runnable() {
              @Override
              public void run() {
                commitToOriginalInner();
              }
            });
          }
        }.execute();
      }
      finally {
        if (!myReleased) myOrigDocument.addDocumentListener(this);
      }
    }

    private void commitToOriginalInner() {
      final String text = myNewDocument.getText();
      final Map<PsiLanguageInjectionHost, Set<Map.Entry<SmartPsiElementPointer, Pair<RangeMarker, RangeMarker>>>> map = ContainerUtil
        .classify(myMarkers.entrySet().iterator(),
                  new Convertor<Map.Entry<SmartPsiElementPointer, Pair<RangeMarker, RangeMarker>>, PsiLanguageInjectionHost>() {
                    public PsiLanguageInjectionHost convert(final Map.Entry<SmartPsiElementPointer, Pair<RangeMarker, RangeMarker>> o) {
                      final PsiElement element = o.getKey().getElement();
                      return (PsiLanguageInjectionHost)element;
                    }
                  });
      PsiDocumentManager.getInstance(myProject).commitDocument(myOrigDocument);
      int localInsideFileCursor = 0;
      for (PsiLanguageInjectionHost host : map.keySet()) {
        if (host == null) continue;
        final String hostText = host.getText();
        TextRange insideHost = null;
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<SmartPsiElementPointer, Pair<RangeMarker, RangeMarker>> entry : map.get(host)) {
          final RangeMarker origMarker = entry.getValue().first;
          final int hostOffset = host.getTextRange().getStartOffset();
          final TextRange localInsideHost =
            new TextRange(origMarker.getStartOffset() - hostOffset, origMarker.getEndOffset() - hostOffset);
          final RangeMarker rangeMarker = entry.getValue().second;
          final TextRange localInsideFile = new TextRange(Math.max(localInsideFileCursor, rangeMarker.getStartOffset()), rangeMarker.getEndOffset());
          if (insideHost != null) {
            //append unchanged inter-markers fragment
            sb.append(hostText.substring(insideHost.getEndOffset(), localInsideHost.getStartOffset()));
          }
          sb.append(localInsideFile.getEndOffset() <= text.length() && !localInsideFile.isEmpty()? localInsideFile.substring(text) : "");
          localInsideFileCursor = localInsideFile.getEndOffset();
          insideHost = insideHost == null ? localInsideHost : insideHost.union(localInsideHost);
        }
        assert insideHost != null;
        ElementManipulators.getManipulator(host).handleContentChange(host, insideHost, sb.toString());
      }
    }

    @Override
    public void dispose() {
      // noop
    }
  }

  private static Balloon.Position getBalloonPosition(Editor editor) {
    final int line = editor.getCaretModel().getVisualPosition().line;
    final Rectangle area = editor.getScrollingModel().getVisibleArea();
    int startLine  = area.y / editor.getLineHeight() + 1;
    return (line - startLine) * editor.getLineHeight() < 200 ? Balloon.Position.below : Balloon.Position.above;
  }
}
