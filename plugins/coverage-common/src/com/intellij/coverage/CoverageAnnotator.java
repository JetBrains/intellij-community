package com.intellij.coverage;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public interface CoverageAnnotator {
  /**
   *
   * @param directory  {@link PsiDirectory} to obtain coverage information for
   * @return human-readable coverage information
   */
  @Nullable
  @Nls
  String getDirCoverageInformationString(@NotNull PsiDirectory directory, @NotNull CoverageSuitesBundle currentSuite,
                                         @NotNull CoverageDataManager manager);

  /**
   *
   * @param file {@link PsiFile} to obtain coverage information for
   * @return human-readable coverage information
   */
  @Nullable
  @Nls
  String getFileCoverageInformationString(@NotNull PsiFile file, @NotNull CoverageSuitesBundle currentSuite,
                                          @NotNull CoverageDataManager manager);

  void onSuiteChosen(@Nullable CoverageSuitesBundle newSuite);

  void renewCoverageData(@NotNull CoverageSuitesBundle suite, @NotNull CoverageDataManager dataManager);
}
