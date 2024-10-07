// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class UnsupportedCharacterInspection extends PropertiesInspectionBase {
  private static final Charset OLD_JAVA_DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;
  private static final Charset NEW_JAVA_DEFAULT_CHARSET = StandardCharsets.UTF_8;

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        if (PsiUtil.isAvailable(JavaFeature.UTF8_PROPERTY_FILES, element)) return;
        PsiReference[] references = element.getReferences();
        for (PsiReference reference : references) {
          if (reference instanceof PropertyReference propertyReference) {
            if (!(propertyReference.resolve() instanceof Property property)) continue;
            if (elementHasError(element, property)) return;
          }
        }
      }

      private boolean elementHasError(@NotNull PsiElement element, @Nullable Property property) {
        if (property == null) return false;
        PsiFile psiFile = property.getContainingFile();
        if (psiFile.getFileType() != PropertiesFileType.INSTANCE) return false;
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) return false;
        EncodingRegistry encoding = EncodingRegistry.getInstance();
        boolean isCustomized = encoding.getDefaultCharsetForPropertiesFiles(file) != null ||
                               encoding.getEncoding(file, true) != NEW_JAVA_DEFAULT_CHARSET;
        return !isCustomized && hasErrorCharacter(element, property.getValue());
      }

      private boolean hasErrorCharacter(@NotNull PsiElement element, @Nullable String value) {
        if (value == null) return false;
        TextRange error = CharsetUtil.findUnmappableCharacters(value, OLD_JAVA_DEFAULT_CHARSET);
        if (error == null) return false;
        holder.registerProblem(element, JavaI18nBundle.message("unsupported.character.problem.descriptor",
                                                               OLD_JAVA_DEFAULT_CHARSET.displayName()),
                               ProblemHighlightType.WEAK_WARNING, new EncodePropertyFix());
        return true;
      }
    };
  }

  private static final class EncodePropertyFix extends PsiUpdateModCommandQuickFix {

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (PsiUtil.isAvailable(JavaFeature.UTF8_PROPERTY_FILES, element)) return;
      PsiReference[] references = element.getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PropertyReference propertyReference &&
            propertyReference.resolve() instanceof Property property) {
          String propertyValue = property.getValue();
          TextRange errorValue = CharsetUtil.findUnmappableCharacters(propertyValue, OLD_JAVA_DEFAULT_CHARSET);
          if (errorValue != null) {
            ByteBuffer encoded = Native2AsciiCharset.wrap(OLD_JAVA_DEFAULT_CHARSET).encode(propertyValue);
            String newValue = OLD_JAVA_DEFAULT_CHARSET.decode(encoded).toString();
            updater.getWritable(property).setValue(newValue);
          }
        }
      }
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaI18nBundle.message("unsupported.character.inspection.fix.description");
    }
  }
}
