/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GroovyMoveStatementHandler extends EditorWriteActionHandler {

  protected EditorActionHandler myOriginalHandler;
  private final boolean isDown;

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown.GroovyMoveStatementHandler");

  public GroovyMoveStatementHandler(EditorActionHandler originalHandler, boolean down) {
    myOriginalHandler = originalHandler;
    isDown = down;
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return HandlerUtils.isEnabled(editor, dataContext, myOriginalHandler);
  }


  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (isEnabled(editor, dataContext) &&
        executeWriteActionInner(editor, dataContext)) return;

    if (myOriginalHandler.isEnabled(editor, dataContext)) {
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  private boolean executeWriteActionInner(Editor editor, DataContext dataContext) {
    if (!HandlerUtils.canBeInvoked(editor, dataContext) ||
        HandlerUtils.getLanguage(dataContext) != GroovyFileType.GROOVY_FILE_TYPE.getLanguage()) {
      return false;
    }

    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    PsiFile file = documentManager.getPsiFile(document);
    if (file == null || !(file instanceof GroovyFile)) return false;

    final Mover mover = getSuitableMover(editor, (GroovyFile) file);
    if (mover == null) return false;
    mover.move(editor, file);

    return true;
  }

  private Mover getSuitableMover(final Editor editor, final GroovyFile file) {
    // order is important
    Mover[] movers = new Mover[]{new StatementMover(isDown), new DeclarationMover(isDown)};
    for (final Mover mover : movers) {
      final boolean available = mover.checkAvailable(editor, file);
      if (available) return mover;
    }
    return null;
  }

}
