package com.intellij.coverage;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public interface CoverageAnnotator {
  /**
   *
   * @param directory  {@link com.intellij.psi.PsiDirectory} to obtain coverage information for
   * @param manager
   * @return human-readable coverage information
   */
  @Nullable
  String getDirCoverageInformationString(@NotNull PsiDirectory directory, @NotNull CoverageSuitesBundle currentSuite,
                                         @NotNull CoverageDataManager manager);

  /**
   *
   * @param file {@link com.intellij.psi.PsiFile} to obtain coverage information for
   * @param manager
   * @return human-readable coverage information
   */
  @Nullable
  String getFileCoverageInformationString(@NotNull PsiFile file, @NotNull CoverageSuitesBundle currentSuite,
                                          @NotNull CoverageDataManager manager);

  void onSuiteChosen(@Nullable CoverageSuitesBundle newSuite);

  void renewCoverageData(@NotNull CoverageSuitesBundle suite, @NotNull CoverageDataManager dataManager);
}
