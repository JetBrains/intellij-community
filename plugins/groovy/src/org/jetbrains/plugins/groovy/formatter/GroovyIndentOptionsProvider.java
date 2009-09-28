package org.jetbrains.plugins.groovy.formatter;

import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ilyas
 */
public class GroovyIndentOptionsProvider implements FileTypeIndentOptionsProvider {
  public CodeStyleSettings.IndentOptions createIndentOptions() {
    final CodeStyleSettings.IndentOptions indentOptions = new CodeStyleSettings.IndentOptions();
    indentOptions.INDENT_SIZE = 2;
    return indentOptions;
  }

  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public IndentOptionsEditor createOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  public String getPreviewText() {
    return "def foo(int arg) {\n" +
           "  return Math.max(arg,\n" +
           "      0)\n" +
           "}";
  }

  public void prepareForReformat(final PsiFile psiFile) {
  }
}
