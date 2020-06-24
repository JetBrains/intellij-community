// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.font.CompositeFont;
import sun.font.Font2D;
import sun.font.FontSubstitution;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;

public class ShowFontsUsedByEditorAction extends EditorAction {
  private static final Logger LOG = Logger.getInstance(ShowFontsUsedByEditorAction.class);

  public ShowFontsUsedByEditorAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return editor instanceof EditorEx;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      new Task.Modal(editor.getProject(), EditorBundle.message("fonts.used.by.editor.progress"), true) {
        private String textToShow;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          Set<String> fontNames = ReadAction.compute(() -> collectFontNames((EditorEx)editor));
          textToShow = StringUtil.join(fontNames, "\n");
        }

        @Override
        public void onSuccess() {
          if (textToShow != null) {
            new MyDialog(textToShow).show();
          }
        }
      }.queue();
    }

    private static Set<String> collectFontNames(@NotNull EditorEx editor) {
      Set<String> result = new TreeSet<>();
      Document document = editor.getDocument();
      CharSequence text = document.getImmutableCharSequence();
      int textLength = document.getTextLength();
      IterationState it = new IterationState(editor, 0, textLength, null, false, true, false, false);
      FontFallbackIterator ffi = new FontFallbackIterator().setPreferredFonts(editor.getColorsScheme().getFontPreferences());
      while (!it.atEnd()) {
        ffi.setFontStyle(it.getMergedAttributes().getFontType());
        int start = it.getStartOffset();
        int end = it.getEndOffset();
        for (int i = start; i < end; i++) {
          if ("\r\n\t".indexOf(text.charAt(i)) >= 0) {
            collectFontNames(result, text, start, i, ffi);
            start = i + 1;
          }
        }
        collectFontNames(result, text, start, end, ffi);
        setProgress((double)end / textLength);
        it.advance();
      }
      return result;
    }

    private static void collectFontNames(@NotNull Set<String> result,
                                         @NotNull CharSequence text,
                                         int startOffset,
                                         int endOffset,
                                         @NotNull FontFallbackIterator ffi) {
      if (startOffset >= endOffset) return;
      ffi.start(text, startOffset, endOffset);
      while (!ffi.atEnd()) {
        ProgressManager.checkCanceled();
        Font font = ffi.getFont();
        List<String> components = null;
        try {
          components = AccessingInternalJdkFontApi.getRelevantComponents(font, text, ffi.getStart(), ffi.getEnd());
        }
        catch (Throwable e) {
          LOG.debug(e);
        }
        if (components == null) {
          result.add(font.getFontName() + " (*)");
        }
        else {
          result.addAll(components);
        }
        ffi.advance();
      }
    }

    private static void setProgress(double progress) {
      ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      if (indicator != null) {
        indicator.setFraction(progress);
      }
    }

    private static final class MyDialog extends DialogWrapper {
      private final JBTextArea myTextArea;

      private MyDialog(String text) {
        super(false);
        setTitle(EditorBundle.message("fonts.used.by.editor.title"));
        myTextArea = new JBTextArea(text, 10, 50);
        myTextArea.setEditable(false);
        init();
      }

      @Override
      protected Action @NotNull [] createActions() {
        return new Action[0];
      }

      @Override
      protected JComponent createCenterPanel() {
        return new JBScrollPane(myTextArea);
      }
    }
  }
}

final class AccessingInternalJdkFontApi {
  private static final Method GET_FONT_2D_METHOD = ReflectionUtil.getDeclaredMethod(Font.class, "getFont2D");
  private static final FontRenderContext DUMMY_CONTEXT = new FontRenderContext(null, false, false);

  @SuppressWarnings("InstanceofIncompatibleInterface")
  static List<String> getRelevantComponents(@NotNull Font font, @NotNull CharSequence text, int startOffset, int endOffset)
    throws Exception {
    if (GET_FONT_2D_METHOD != null) {
      Font2D font2D = (Font2D)GET_FONT_2D_METHOD.invoke(font);
      if (font2D != null) {
        CompositeFont compositeFont = null;
        IntUnaryOperator charToGlyphMapper = null;
        if (font2D instanceof CompositeFont) {
          compositeFont = (CompositeFont)font2D;
          charToGlyphMapper = c -> font2D.charToGlyph(c);
        }
        else if (font2D instanceof FontSubstitution) {
          compositeFont = ((FontSubstitution)font2D).getCompositeFont2D();
          charToGlyphMapper = c -> font.createGlyphVector(DUMMY_CONTEXT, new String(new int[]{c}, 0, 1)).getGlyphCode(0);
        }
        List<Font2D> components = new ArrayList<>();
        if (compositeFont == null) {
          components.add(font2D);
        }
        else {
          for (int i = startOffset; i < endOffset; ) {
            int codePoint = Character.codePointAt(text, i);
            int glyph = charToGlyphMapper.applyAsInt(codePoint);
            int slot = glyph >>> 24;
            components.add(compositeFont.getSlotFont(slot));
            i += Character.charCount(codePoint);
          }
        }
        return ContainerUtil.map(components, f -> f.getFontName(null));
      }
    }
    return null;
  }
}
