package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntImport;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class AntFileReference extends FileReferenceBase implements AntReference {
  @NonNls private static final String BASEDIR_PROPERTY_REFERENCE = "${basedir}";

  public AntFileReference(final AntFileReferenceSet set, final TextRange range, final int index, final String text) {
    super(set, range, index, text);
  }

  @Nullable
  protected String getText() {
    final String path = super.getText();
    if (getIndex() == 0 && path != null && path.equals(BASEDIR_PROPERTY_REFERENCE) && getFileReferenceSet().isAbsolutePathReference()) {
      return ".";
    }
    final String _path = getElement().computeAttributeValue(path);
    return _path != null? FileUtil.toSystemIndependentName(_path) : null;
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  @NotNull
  public AntFileReferenceSet getFileReferenceSet() {
    return (AntFileReferenceSet)super.getFileReferenceSet();
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("file.doesnt.exist", getCanonicalRepresentationText());
  }

  public boolean shouldBeSkippedByAnnotator() {
    return isSoft();
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {
  }

  @NotNull
  public IntentionAction[] getFixes() {
    return EMPTY_INTENTIONS;
  }

  @Nullable
  public String getCanonicalRepresentationText() {
    final AntStructuredElement element = getElement();
    final String value = getCanonicalText();
    return element.computeAttributeValue(value);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntStructuredElement antElement = getElement();
    final PsiElement element = getManipulatorElement();
    CachingReference.getManipulator(element).handleContentChange(element, getRangeInElement().shiftRight(
      antElement.getTextRange().getStartOffset() - element.getTextRange().getStartOffset()), newElementName);
    return antElement;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element");
    final VirtualFile dstVFile = PsiUtil.getVirtualFile(element);
    final AntStructuredElement se = getElement();
    final PsiFile file = se.getContainingFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);
    VirtualFile currentFile = file.getVirtualFile();
    if (!(se instanceof AntImport)) {
      final String baseDir = se.getAntProject().getBaseDir();
      if (baseDir != null && baseDir.length() > 0) {
        final File f = new File(currentFile.getParent().getPath(), baseDir);
        currentFile = LocalFileSystem.getInstance().findFileByPath(f.getAbsolutePath().replace(File.separatorChar, '/'));
      }
    }
    final String newName = VfsUtil.getPath(currentFile, dstVFile, '/');
    if (newName == null) {
      throw new IncorrectOperationException(
        "Cannot find path between files; src = " + currentFile.getPresentableUrl() + "; dst = " + dstVFile.getPresentableUrl());
    }
    final PsiElement me = getManipulatorElement();
    TextRange range = new TextRange(getFileReferenceSet().getStartInElement(), getRangeInElement().getEndOffset());
    range = range.shiftRight(se.getTextRange().getStartOffset() - me.getTextRange().getStartOffset());
    return CachingReference.getManipulator(me).handleContentChange(me, range, newName);
  }

  protected ResolveResult[] innerResolve() {
    final ResolveResult[] resolveResult = super.innerResolve();
    if (resolveResult.length == 0) {
      final String text = getText();
      if (text != null && text.length() > 0 && getFileReferenceSet().isAbsolutePathReference()) {
        final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(text));
        if (file != null) {
          final PsiManager psiManager = getElement().getManager();
          final PsiFileSystemItem fsItem = file.isDirectory()? psiManager.findDirectory(file) : psiManager.findFile(file);
          if (fsItem != null) {
            return new ResolveResult[]{new PsiElementResolveResult(fsItem)};
          }
        }
      }
    }
    return resolveResult;
  }

  private PsiElement getManipulatorElement() {
    return getFileReferenceSet().getManipulatorElement();
  }
}