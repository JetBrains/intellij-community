// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.io.IOUtil;
import com.intellij.xml.util.XmlUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConvertToBasicLatinInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final TokenSet LITERALS = TokenSet.create(JavaTokenType.CHARACTER_LITERAL, JavaTokenType.STRING_LITERAL);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      private void handle(@NotNull PsiElement element) {
        if (IOUtil.isAscii(element.getText())) return;
        // "Basic Latin" is a proper noun
        //noinspection DialogTitleCapitalization
        holder.registerProblem(element, JavaI18nBundle.message("inspection.non.basic.latin.character.display.name"), new ConvertToBasicLatinFix());
      }

      @Override
      public void visitComment(@NotNull PsiComment comment) {
        super.visitComment(comment);
        handle(comment);
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        super.visitLiteralExpression(expression);
        if (!(expression instanceof PsiLiteralExpressionImpl)) return;
        if (!LITERALS.contains(((PsiLiteralExpressionImpl)expression).getLiteralElementType())) {
          return;
        }
        handle(expression);
      }

      @Override
      public void visitDocComment(@NotNull PsiDocComment comment) {
        super.visitDocComment(comment);
        handle(comment);
      }
    };
  }

  private abstract static class Handler {
    @NotNull
    PsiElement getSubstitution(@NotNull Project project, @NotNull PsiElement element) {
      String text = element.getText();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (isBasicLatin(ch)) {
          sb.append(ch);
        }
        else {
          convert(sb, ch);
        }
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      return getSubstitution(factory, element, sb.toString());
    }

    protected static boolean isBasicLatin(char ch) {
      return Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.BASIC_LATIN;
    }

    protected abstract void convert(@NotNull StringBuilder sb, char ch);

    @NotNull
    protected abstract PsiElement getSubstitution(@NotNull PsiElementFactory factory, @NotNull PsiElement element, @NotNull String newText);
  }

  private static class LiteralHandler extends Handler {
    @Override
    protected @NotNull PsiElement getSubstitution(@NotNull PsiElementFactory factory,
                                                  @NotNull PsiElement element,
                                                  @NotNull String newText) {
      return factory.createExpressionFromText(newText, element.getParent());
    }

    @Override
    protected void convert(@NotNull StringBuilder sb, char ch) {
      sb.append(String.format("\\u%04X", (int)ch));
    }
  }

  private static class DocCommentHandler extends Handler {
    private static Int2ObjectMap<String> ourEntities;

    @Override
    @NotNull
    PsiElement getSubstitution(@NotNull Project project, @NotNull PsiElement element) {
      loadEntities(project);
      return ourEntities != null ? super.getSubstitution(project, element) : element;
    }

    @Override
    protected void convert(@NotNull StringBuilder sb, char ch) {
      String entity = ourEntities.get(ch);
      if (entity != null) {
        sb.append('&').append(entity).append(';');
      }
      else {
        sb.append("&#x").append(Integer.toHexString(ch).toUpperCase(Locale.ENGLISH)).append(';');
      }
    }

    @Override
    protected @NotNull PsiElement getSubstitution(@NotNull PsiElementFactory factory,
                                                  @NotNull PsiElement element,
                                                  @NotNull String newText) {
      return factory.createCommentFromText(newText, element.getParent());
    }

    private static void loadEntities(@NotNull Project project) {
      if (ourEntities != null) return;

      XmlFile file;
      try {
        String url = ExternalResourceManager.getInstance().getResourceLocation(XmlUtil.HTML4_LOOSE_URI, project);
        if (url == null) {
          Logger.getInstance(ConvertToBasicLatinInspection.class).error("Namespace not found: " + XmlUtil.HTML4_LOOSE_URI);
          return;
        }
        VirtualFile vFile = VfsUtil.findFileByURL(new URL(url));
        if (vFile == null) {
          Logger.getInstance(ConvertToBasicLatinInspection.class).error("Resource not found: " + url);
          return;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (!(psiFile instanceof XmlFile)) {
          Logger.getInstance(ConvertToBasicLatinInspection.class).error("Unexpected resource: " + psiFile);
          return;
        }
        file = (XmlFile)psiFile;
      }
      catch (MalformedURLException e) {
        Logger.getInstance(ConvertToBasicLatinInspection.class).error(e);
        return;
      }

      Int2ObjectMap<String> entities = new Int2ObjectOpenHashMap<>();
      Pattern pattern = Pattern.compile("&#(\\d+);");
      XmlUtil.processXmlElements(file, element -> {
        if (element instanceof XmlEntityDecl) {
          XmlEntityDecl entity = (XmlEntityDecl)element;
          Matcher m = pattern.matcher(entity.getValueElement().getValue());
          if (m.matches()) {
            char i = (char)Integer.parseInt(m.group(1));
            if (!isBasicLatin(i)) {
              entities.put(i, entity.getName());
            }
          }
        }
        return true;
      }, true);

      ourEntities = entities;
    }
  }

  private static class CommentHandler extends DocCommentHandler {
  }

  private static class ConvertToBasicLatinFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaI18nBundle.message("inspection.non.basic.latin.character.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final Handler handler;
      if (element instanceof PsiLiteralExpression) {
        handler = new LiteralHandler();
      }
      else if (element instanceof PsiDocComment) {
        handler = new DocCommentHandler();
      }
      else if (element instanceof PsiComment) {
        handler = new CommentHandler();
      }
      else {
        handler = null;
      }
      if (handler == null) return;
      final PsiElement newElement = handler.getSubstitution(project, element);
      element.replace(newElement);
    }
  }
}