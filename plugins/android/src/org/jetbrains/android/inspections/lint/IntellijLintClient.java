package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.LintCliXmlParser;
import com.android.tools.lint.LombokParser;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class IntellijLintClient extends LintClient implements Disposable {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.IntellijLintClient");

  private final State myState;

  public IntellijLintClient(@NotNull State state) {
    myState = state;
  }

  @Override
  public Configuration getConfiguration(com.android.tools.lint.detector.api.Project project) {
    return new IntellijLintConfiguration(myState.getIssues());
  }

  @Override
  public void report(Context context, Issue issue, Severity severity, Location location, String message, Object data) {
    if (location != null) {
      final File file = location.getFile();

      if (file != null) {
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

        if (myState.getMainFile().equals(vFile)) {
          final Position start = location.getStart();
          final Position end = location.getEnd();

          final TextRange textRange = start != null && end != null
                                      ? new TextRange(start.getOffset(), end.getOffset())
                                      : TextRange.EMPTY_RANGE;

          myState.getProblems().add(new ProblemData(issue, message, textRange));
        }
      }
    }
  }

  @Override
  public void log(Severity severity, Throwable exception, String format, Object... args) {
    // todo: log
  }

  @Override
  public IDomParser getDomParser() {
    return new LintCliXmlParser();
  }

  @Override
  public IJavaParser getJavaParser() {
    return new LombokParser();
  }

  @Override
  public List<File> getJavaClassFolders(com.android.tools.lint.detector.api.Project project) {
    // todo: implement when class files checking detectors will be available
    return Collections.emptyList();
  }

  @Override
  public List<File> getJavaLibraries(com.android.tools.lint.detector.api.Project project) {
    // todo: implement
    return Collections.emptyList();
  }

  @Override
  public List<File> getJavaSourceFolders(com.android.tools.lint.detector.api.Project project) {
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myState.getModule()).getSourceRoots(false);
    final List<File> result = new ArrayList<File>(sourceRoots.length);

    for (VirtualFile root : sourceRoots) {
      result.add(new File(root.getPath()));
    }
    return result;
  }

  @Override
  @NotNull
  public String readFile(File file) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

    if (vFile == null) {
      LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
      return "";
    }
    final String content = getFileContent(vFile);

    if (content == null) {
      LOG.info("Cannot find file " + file.getPath() + " in the PSI");
      return "";
    }
    return content;
  }

  @Nullable
  private String getFileContent(final VirtualFile vFile) {
    if (myState.getMainFile() == vFile) {
      return myState.getMainFileContent();
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        final Module module = myState.getModule();
        final Project project = module.getProject();
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);

        if (psiFile == null) {
          return null;
        }
        final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

        if (document != null) {
          final DocumentListener listener = new DocumentListener() {
            @Override
            public void beforeDocumentChange(DocumentEvent event) {
            }

            @Override
            public void documentChanged(DocumentEvent event) {
              myState.markDirty();
            }
          };
          document.addDocumentListener(listener, IntellijLintClient.this);
        }
        return psiFile.getText();
      }
    });
  }

  @Override
  public void dispose() {
  }
}
