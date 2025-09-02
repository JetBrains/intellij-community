import com.intellij.codeInsight.daemon.impl.analysis.JavaErrorFixProvider;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class MyJavaErrorFix implements IntentionAction {

  @Override
  public @NotNull String getText() { return "text"; }
  @Override
  public @NotNull String getFamilyName() { return "familyName"; }
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {return true; }
  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {}
  @Override
  public boolean startInWriteAction() { return true; }

  public static class MyProvider implements JavaErrorFixProvider {
    @Override
    public void registerFixes(@NotNull JavaCompilationError<?, ?> error,
                              @NotNull Consumer<? super @NotNull CommonIntentionAction> sink) {
      error.psiForKind(JavaErrorKinds.EXCEPTION_UNHANDLED)
        .ifPresent(e -> sink.accept(new MyJavaErrorFix()));
    }
  }
}