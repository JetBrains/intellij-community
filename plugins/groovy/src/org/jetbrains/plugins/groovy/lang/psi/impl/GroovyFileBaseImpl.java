/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.*;

/**
 * @author ilyas
 */
public abstract class GroovyFileBaseImpl extends PsiFileBase implements GroovyFileBase, GrControlFlowOwner {

  private GrMethod[] myMethods = null;

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myMethods = null;
  }

  protected GroovyFileBaseImpl(FileViewProvider viewProvider, @NotNull Language language) {
    super(viewProvider, language);
  }

  public GroovyFileBaseImpl(IFileElementType root, IFileElementType root1, FileViewProvider provider) {
    this(provider, root.getLanguage());
    init(root, root1);
  }

  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }

  public GrTypeDefinition[] getTypeDefinitions() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyElementTypes.TYPE_DEFINITION_TYPES, GrTypeDefinition.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyElementTypes.TYPE_DEFINITION_TYPES, GrTypeDefinition.ARRAY_FACTORY);
  }

  public GrTopLevelDefinition[] getTopLevelDefinitions() {
    return findChildrenByClass(GrTopLevelDefinition.class);
  }

  public GrMethod[] getCodeMethods() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyElementTypes.METHOD_DEFINITION, GrMethod.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyElementTypes.METHOD_DEFINITION, GrMethod.ARRAY_FACTORY);
  }

  @Override
  public GrMethod[] getMethods() {
    if (myMethods == null) {
      List<GrMethod> result = new ArrayList<GrMethod>();
      
      GrMethod[] methods = getCodeMethods();
      for (GrMethod method : methods) {
        final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
        if (reflectedMethods.length > 0) {
          result.addAll(Arrays.asList(reflectedMethods));
        }
        else {
          result.add(method);
        }
      }

      myMethods = result.toArray(new GrMethod[result.size()]);
    }
    return myMethods;
  }

  public GrTopStatement[] getTopStatements() {
    return findChildrenByClass(GrTopStatement.class);
  }

  public boolean importClass(PsiClass aClass) {
    return addImportForClass(aClass) != null;
  }

  public void removeImport(GrImportStatement importStatement) throws IncorrectOperationException {
    PsiElement before = importStatement;
    while (isWhiteSpace(before.getPrevSibling())) {
      before = before.getPrevSibling();
    }

    if (hasElementType(before.getPrevSibling(), GroovyTokenTypes.mSEMI)) before = before.getPrevSibling();
    if (isWhiteSpace(before.getPrevSibling())) before = before.getPrevSibling();

    PsiElement after = importStatement;
    if (isWhiteSpace(after.getNextSibling())) after = after.getNextSibling();
    if (hasElementType(after.getNextSibling(), GroovyTokenTypes.mSEMI)) after = after.getNextSibling();
    while (isWhiteSpace(after.getNextSibling())) after = after.getNextSibling();


    if (before == null) before = importStatement;

    PsiElement anchor_before = before.getPrevSibling();
    PsiElement anchor_after = after.getNextSibling();
    if (before == after) {
      importStatement.delete();
    }
    else {
      deleteChildRange(before, after);
    }

    if (anchor_before instanceof GrImportStatement && anchor_after instanceof GrImportStatement) {
      addLineFeedAfter((GrImportStatement)anchor_before);
    }
    else if (anchor_before != null && anchor_after != null) {
      String text = anchor_after instanceof GrTopStatement && anchor_before instanceof GrTopStatement ? "\n\n" : "\n";
      getNode().addLeaf(GroovyTokenTypes.mNLS, text, anchor_after.getNode());
    }
  }

  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element.isValid()) {
        if (element.getParent() != this) throw new IncorrectOperationException();
        deleteChildRange(element, element);
      }
    }
  }

  @NotNull
  @Override
  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  @NotNull
  public GrStatement addStatementBefore(@NotNull GrStatement statement, @Nullable GrStatement anchor) throws IncorrectOperationException {
    final PsiElement result = addBefore(statement, anchor);
    if (anchor != null) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchor.getNode());
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", result.getNode());
    }
    return (GrStatement)result;
  }

  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitFile(this);
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @NotNull
  public PsiClass[] getClasses() {
    return getTypeDefinitions();
  }

  public void clearCaches() {
    super.clearCaches();
    myControlFlow = null;
  }

  private volatile SoftReference<Instruction[]> myControlFlow = null;

  public Instruction[] getControlFlow() {
    assert isValid();
    SoftReference<Instruction[]> flow = myControlFlow;
    Instruction[] result = flow != null ? flow.get() : null;
    if (result == null) {
      result = new ControlFlowBuilder(getProject()).buildControlFlow(this);
      myControlFlow = new SoftReference<Instruction[]>(result);
    }
    return ControlFlowBuilder.assertValidPsi(result);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return false;
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrTopStatement) {
      deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }

  protected void addLineFeedBefore(GrImportStatement result) {
    final GroovyCodeStyleSettings settings =
      CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().getCustomSettings(GroovyCodeStyleSettings.class);
    final PackageEntryTable layoutTable = settings.IMPORT_LAYOUT_TABLE;
    final PackageEntry[] entries = layoutTable.getEntries();

    PsiElement prev = result.getPrevSibling();
    while (isWhiteSpace(prev)) {
      prev = prev.getPrevSibling();
    }
    if (hasElementType(prev, GroovyTokenTypes.mSEMI)) prev = prev.getPrevSibling();
    if (isWhiteSpace(prev)) prev = prev.getPrevSibling();

    if (prev instanceof GrImportStatement) {
      final int idx_before = getPackageEntryIdx(entries, (GrImportStatement)prev);
      final int idx = getPackageEntryIdx(entries, result);
      final int spaceCount = getMaxSpaceCount(entries, idx_before, idx);

      //skip space and semicolon after import
      if (isWhiteSpace(prev.getNextSibling()) && hasElementType(prev.getNextSibling().getNextSibling(), GroovyTokenTypes.mSEMI)) prev = prev.getNextSibling().getNextSibling();
      final FileASTNode node = getNode();
      while (isWhiteSpace(prev.getNextSibling())) {
        node.removeChild(prev.getNextSibling().getNode());
      }
      node.addLeaf(GroovyTokenTypes.mNLS, StringUtil.repeat("\n", spaceCount + 1), result.getNode());
    }
  }

  protected void addLineFeedAfter(GrImportStatement result) {
    final GroovyCodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().getCustomSettings(GroovyCodeStyleSettings.class);
    final PackageEntryTable layoutTable = settings.IMPORT_LAYOUT_TABLE;
    final PackageEntry[] entries = layoutTable.getEntries();

    PsiElement next = result.getNextSibling();
    if (isWhiteSpace(next)) next = next.getNextSibling();
    if (hasElementType(next, GroovyTokenTypes.mSEMI)) next = next.getNextSibling();
    while (isWhiteSpace(next)) {
      next = next.getNextSibling();
    }
    if (next instanceof GrImportStatement) {
      final int idx_after = getPackageEntryIdx(entries, (GrImportStatement)next);
      final int idx = getPackageEntryIdx(entries, result);
      final int spaceCount = getMaxSpaceCount(entries, idx, idx_after);


      final FileASTNode node = getNode();
      while (isWhiteSpace(next.getPrevSibling())) {
        node.removeChild(next.getPrevSibling().getNode());
      }
      node.addLeaf(GroovyTokenTypes.mNLS, StringUtil.repeat("\n", spaceCount + 1), next.getNode());
    }
  }

  protected static int getPackageEntryIdx(PackageEntry[] entries, GrImportStatement statement) {
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
}
