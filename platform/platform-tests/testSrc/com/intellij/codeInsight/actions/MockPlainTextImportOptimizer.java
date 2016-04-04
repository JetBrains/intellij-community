package com.intellij.codeInsight.actions;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class MockPlainTextImportOptimizer implements ImportOptimizer {
  private Set<PsiFile> myProcessedFiles = new HashSet<>();
  
  @Override
  public boolean supports(PsiFile file) {
    return file.getLanguage() == PlainTextLanguage.INSTANCE;
  }

  @NotNull
  @Override
  public Runnable processFile(PsiFile file) {
    myProcessedFiles.add(file);
    return EmptyRunnable.INSTANCE;
  }
  
  @NotNull
  public Set<PsiFile> getProcessedFiles() {
    return myProcessedFiles;
  }
}
