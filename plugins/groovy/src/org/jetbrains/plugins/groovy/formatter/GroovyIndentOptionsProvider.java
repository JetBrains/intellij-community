/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    return new CodeStyleSettings.IndentOptions();
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
