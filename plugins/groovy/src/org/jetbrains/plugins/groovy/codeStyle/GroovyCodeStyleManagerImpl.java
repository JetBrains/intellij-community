/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.Comparator;

public class GroovyCodeStyleManagerImpl extends GroovyCodeStyleManager {
  @NotNull
  @Override
  public GrImportStatement addImport(@NotNull GroovyFile psiFile, @NotNull GrImportStatement statement) throws IncorrectOperationException {
    PsiElement anchor = getAnchorToInsertImportAfter(psiFile, statement);
    final PsiElement result = psiFile.addAfter(statement, anchor);

    final GrImportStatement gImport = (GrImportStatement)result;
    addLineFeedBefore(psiFile, gImport);
    addLineFeedAfter(psiFile, gImport);
    return gImport;
  }

  @Nullable
  private PsiElement getShellComment(@NotNull PsiElement psiFile) {
    final ASTNode node = psiFile.getNode().findChildByType(GroovyTokenTypes.mSH_COMMENT);
    return node == null ? null : node.getPsi();
  }

  @Nullable
  private PsiElement getAnchorToInsertImportAfter(@NotNull GroovyFile psiFile, @NotNull GrImportStatement statement) {
    final GroovyCodeStyleSettings settings = CodeStyleSettingsManager.getInstance(psiFile.getProject()).getCurrentSettings().getCustomSettings(
      GroovyCodeStyleSettings.class);
    final PackageEntryTable layoutTable = settings.IMPORT_LAYOUT_TABLE;
    final PackageEntry[] entries = layoutTable.getEntries();

    GrImportStatement[] importStatements = psiFile.getImportStatements();
    if (importStatements.length == 0) {
      final GrPackageDefinition definition = psiFile.getPackageDefinition();
      if (definition != null) {
        return definition;
      }

      return getShellComment(psiFile);
    }

    final Comparator<GrImportStatement> comparator = GroovyImportOptimizer.getComparator(settings);

    final int idx = getPackageEntryIdx(entries, statement);

    PsiElement anchor = null;

    for (GrImportStatement importStatement : importStatements) {
      final int i = getPackageEntryIdx(entries, importStatement);
      if (i < idx) {
        anchor = importStatement;
      }
      else if (i > idx) {
        break;
      }
      else if (comparator.compare(statement, importStatement) > 0) {
        anchor = importStatement;
      }
      else {
        break;
      }
    }

    if (anchor == null) anchor = psiFile.getPackageDefinition();
    if (anchor == null) anchor = getShellComment(psiFile);
    if (anchor == null && importStatements.length > 0) anchor = importStatements[0].getPrevSibling();
    return anchor;
  }

  protected static int getPackageEntryIdx(@NotNull PackageEntry[] entries, @NotNull GrImportStatement statement) {
    final GrCodeReferenceElement reference = statement.getImportReference();
    if (reference == null) return -1;
    final String packageName = StringUtil.getPackageName(reference.getCanonicalText());
    final boolean isStatic = statement.isStatic();

    int best = -1;
    int allOtherStatic = -1;
    int allOther = -1;
    PackageEntry bestEntry = null;
    for (int i = 0, length = entries.length; i < length; i++) {
      PackageEntry entry = entries[i];
      if (entry.isBetterMatchForPackageThan(bestEntry, packageName, isStatic)) {
        best = i;
        bestEntry = entry;
      }
      else if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
        allOtherStatic = i;
      }
      else if (entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY) {
        allOther = i;
      }
    }
    if (best >= 0) return best;

    if (isStatic && allOtherStatic != -1) return allOtherStatic;
    return allOther;
  }

  protected void addLineFeedBefore(@NotNull PsiElement psiFile, @NotNull GrImportStatement result) {
    final CodeStyleSettings commonSettings = CodeStyleSettingsManager.getInstance(psiFile.getProject()).getCurrentSettings();
    final GroovyCodeStyleSettings settings = commonSettings.getCustomSettings(GroovyCodeStyleSettings.class);

    final PackageEntryTable layoutTable = settings.IMPORT_LAYOUT_TABLE;
    final PackageEntry[] entries = layoutTable.getEntries();

    PsiElement prev = result.getPrevSibling();
    while (PsiImplUtil.isWhiteSpaceOrNls(prev)) {
      prev = prev.getPrevSibling();
    }
    if (PsiImplUtil.hasElementType(prev, GroovyTokenTypes.mSEMI)) prev = prev.getPrevSibling();
    if (PsiImplUtil.isWhiteSpaceOrNls(prev)) prev = prev.getPrevSibling();

    ASTNode node = psiFile.getNode();
    if (prev instanceof GrImportStatement) {
      final int idx_before = getPackageEntryIdx(entries, (GrImportStatement)prev);
      final int idx = getPackageEntryIdx(entries, result);
      final int spaceCount = getMaxSpaceCount(entries, idx_before, idx);

      //skip space and semicolon after import
      if (PsiImplUtil.isWhiteSpaceOrNls(prev.getNextSibling()) && PsiImplUtil
        .hasElementType(prev.getNextSibling().getNextSibling(), GroovyTokenTypes.mSEMI)) prev = prev.getNextSibling().getNextSibling();
      while (PsiImplUtil.isWhiteSpaceOrNls(prev.getNextSibling())) {
        node.removeChild(prev.getNextSibling().getNode());
      }
      node.addLeaf(GroovyTokenTypes.mNLS, StringUtil.repeat("\n", spaceCount + 1), result.getNode());
    } else if (prev instanceof GrPackageDefinition) {
      node.addLeaf(GroovyTokenTypes.mNLS, StringUtil.repeat("\n", commonSettings.getCommonSettings(GroovyLanguage.INSTANCE).BLANK_LINES_AFTER_PACKAGE), result.getNode());
    }
  }

  protected void addLineFeedAfter(@NotNull PsiElement psiFile, GrImportStatement result) {
    final GroovyCodeStyleSettings settings = CodeStyleSettingsManager.getInstance(psiFile.getProject()).getCurrentSettings().getCustomSettings(GroovyCodeStyleSettings.class);
    final PackageEntryTable layoutTable = settings.IMPORT_LAYOUT_TABLE;
    final PackageEntry[] entries = layoutTable.getEntries();

    PsiElement next = result.getNextSibling();
    if (PsiImplUtil.isWhiteSpaceOrNls(next)) next = next.getNextSibling();
    if (PsiImplUtil.hasElementType(next, GroovyTokenTypes.mSEMI)) next = next.getNextSibling();
    while (PsiImplUtil.isWhiteSpaceOrNls(next)) {
      next = next.getNextSibling();
    }
    if (next instanceof GrImportStatement) {
      final int idx_after = getPackageEntryIdx(entries, (GrImportStatement)next);
      final int idx = getPackageEntryIdx(entries, result);
      final int spaceCount = getMaxSpaceCount(entries, idx, idx_after);


      ASTNode node = psiFile.getNode();
      while (PsiImplUtil.isWhiteSpaceOrNls(next.getPrevSibling())) {
        node.removeChild(next.getPrevSibling().getNode());
      }
      node.addLeaf(GroovyTokenTypes.mNLS, StringUtil.repeat("\n", spaceCount + 1), next.getNode());
    }
  }


  private static int getMaxSpaceCount(PackageEntry[] entries, int b1, int b2) {
    int start = Math.min(b1, b2);
    int end = Math.max(b1, b2);

    if (start == -1) return 0;

    int max = 0;
    int cur = 0;
    for (int i = start; i < end; i++) {
      if (entries[i] == PackageEntry.BLANK_LINE_ENTRY) {
        cur++;
      }
      else {
        max = Math.max(max, cur);
        cur = 0;
      }
    }
    max = Math.max(max, cur);
    return max;
  }

  @Override
  public void removeImport(@NotNull GroovyFileBase psiFile, @NotNull GrImportStatement importStatement) throws IncorrectOperationException {
    PsiElement psiElement = psiFile;
    PsiElement before = importStatement;
    while (PsiImplUtil.isWhiteSpaceOrNls(before.getPrevSibling())) {
      before = before.getPrevSibling();
    }

    if (PsiImplUtil.hasElementType(before.getPrevSibling(), GroovyTokenTypes.mSEMI)) before = before.getPrevSibling();
    if (PsiImplUtil.isWhiteSpaceOrNls(before.getPrevSibling())) before = before.getPrevSibling();

    PsiElement after = importStatement;
    if (PsiImplUtil.isWhiteSpaceOrNls(after.getNextSibling())) after = after.getNextSibling();
    if (PsiImplUtil.hasElementType(after.getNextSibling(), GroovyTokenTypes.mSEMI)) after = after.getNextSibling();
    while (PsiImplUtil.isWhiteSpaceOrNls(after.getNextSibling())) after = after.getNextSibling();


    if (before == null) before = importStatement;

    PsiElement anchor_before = before.getPrevSibling();
    PsiElement anchor_after = after.getNextSibling();
    if (before == after) {
      importStatement.delete();
    }
    else {
      psiFile.deleteChildRange(before, after);
    }

    if (anchor_before instanceof GrImportStatement && anchor_after instanceof GrImportStatement) {
      addLineFeedAfter(psiFile, (GrImportStatement)anchor_before);
    }
    else if (anchor_before != null && anchor_after != null) {
      String text = anchor_after instanceof GrTopStatement && anchor_before instanceof GrTopStatement ? "\n\n" : "\n";
      psiElement.getNode().addLeaf(GroovyTokenTypes.mNLS, text, anchor_after.getNode());
    }
  }
}
