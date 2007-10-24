/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.grails.fileType.GspFileType;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ilyas
 */
public class HandlerUtils {

  public static boolean isEnabled(@NotNull final Editor editor, @NotNull final DataContext dataContext,
                                  @NotNull final EditorActionHandler originalHandler) {
    if (getLanguage(dataContext) == GroovyFileType.GROOVY_FILE_TYPE.getLanguage() ||
        getLanguage(dataContext) == GspFileType.GSP_FILE_TYPE.getLanguage()) {
      return true;
    }
    return originalHandler.isEnabled(editor, dataContext);
  }

  public static boolean isReadOnly(@NotNull final Editor editor) {
    if (editor.isViewer()) {
      return true;
    }
    Document document = editor.getDocument();
    return !document.isWritable();
  }

  public static boolean canBeInvoked(final Editor editor, final DataContext dataContext) {
    if (isReadOnly(editor)) {
      return false;
    }
    if (getPsiFile(editor, dataContext) == null) {
      return false;
    }

    return true;
  }

  public static PsiFile getPsiFile(@NotNull final Editor editor, @NotNull final DataContext dataContext) {
    return PsiDocumentManager.getInstance(getProject(dataContext)).getPsiFile(editor.getDocument());
  }

  @Nullable
  public static Language getLanguage(@NotNull final DataContext dataContext) {
    return DataKeys.LANGUAGE.getData(dataContext);
  }

  @Nullable
  public static Project getProject(@NotNull final DataContext dataContext) {
    return DataKeys.PROJECT.getData(dataContext);
  }


}
