package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.LineData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * Allows to customize the Java coverage engine for other JVM-based languages.
 *
 * @author anna
 * @since 2/14/11
 */
public abstract class JavaCoverageEngineExtension {
  public static final ExtensionPointName<JavaCoverageEngineExtension> EP_NAME = ExtensionPointName.create("com.intellij.javaCoverageEngineExtension");

  public abstract boolean isApplicableTo(@Nullable RunConfigurationBase conf);

  /**
   * Calculates the qualified names of class files generated from a source file.
   *
   * @param sourceFile a source file for which the information is requested.
   * @param classes    the classes contained in the file, as returned by {@link com.intellij.psi.PsiClassOwner#getClasses()}
   * @param names      the set into which the qualified names (dot-separated) of the classes need to be placed.
   * @return true if results were provided, false if this file isn't handled by this run configuration.
   */
  public boolean suggestQualifiedName(@NotNull PsiFile sourceFile, PsiClass[] classes, Set<String> names) {
    return false;
  }

  /**
   * Collects the output .class files generated from a specific source file.
   *
   * @param srcFile    the source file.
   * @param output     the output root for the module containing the source file.
   * @param testoutput the test output root for the module containing the source file.
   * @param suite      the coverage suite for which the information is being requested.
   * @param classFiles the set to be filled with class files produced from this source file.
   * @return true if the extension has filled the file list, false if this extension doesn't handle this file type.
   */
  public boolean collectOutputFiles(@NotNull final PsiFile srcFile,
                                    @Nullable final VirtualFile output,
                                    @Nullable final VirtualFile testoutput,
                                    @NotNull final CoverageSuitesBundle suite,
                                    @NotNull final Set<File> classFiles){
    return false;
  }

  /**
   * Returns the text to show when clicking on the coverage gutter. The text usually contains the number of true and false branch hits
   * for conditional statements.
   *
   * @param editor the editor in which the gutter is displayed.
   * @param file the file shown in the editor.
   * @param lineNumber the line number which was clicked.
   * @param startOffset the start offset of that line in the PSI file.
   * @param endOffset the end offset of that line in the PSI file.
   * @param lineData the coverage data for the line.
   * @return the text to show, or null if this extension doesn't handle this file type or doesn't have any custom text to show.
   */
  public String generateBriefReport(Editor editor, PsiFile file, int lineNumber, int startOffset, int endOffset, LineData lineData) {
    return null;
  }

  /**
   * Returns true if this configuration requires the generation of a source map to match the compiled .class files to
   * corresponding sources.
   */
  public boolean isSourceMapNeeded(RunConfigurationBase runConfiguration) {
    return false;
  }
}
