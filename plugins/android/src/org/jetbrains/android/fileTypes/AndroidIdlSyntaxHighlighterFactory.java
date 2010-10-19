package org.jetbrains.android.fileTypes;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidIdlSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
    return new JavaFileHighlighter(LanguageLevel.HIGHEST);
  }
}
